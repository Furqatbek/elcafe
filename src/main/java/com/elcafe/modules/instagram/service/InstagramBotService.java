package com.elcafe.modules.instagram.service;

import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import com.elcafe.modules.instagram.entity.InstagramSubscriberAddress;
import com.elcafe.modules.instagram.repository.InstagramBotConfigRepository;
import com.elcafe.modules.instagram.repository.InstagramSubscriberAddressRepository;
import com.elcafe.modules.instagram.repository.InstagramSubscriberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Manages the Instagram DM registration wizard and outbound messaging.
 *
 * Conversation states
 * ───────────────────
 *  AWAITING_NAME           → user has not yet provided their name
 *  AWAITING_PHONE          → name collected, awaiting phone number
 *  AWAITING_BIRTHDAY       → phone collected, awaiting birthday (skippable)
 *  AWAITING_ADDRESS        → birthday step done, awaiting delivery address text
 *  AWAITING_MORE_ADDRESSES → first address saved, asking if more needed
 *  REGISTERED              → wizard complete
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstagramBotService {

    @Value("${branding.name:Qahvoon}")
    private String brandName;

    private static final String STATE_AWAITING_NAME           = "AWAITING_NAME";
    private static final String STATE_AWAITING_PHONE          = "AWAITING_PHONE";
    private static final String STATE_AWAITING_BIRTHDAY       = "AWAITING_BIRTHDAY";
    private static final String STATE_AWAITING_ADDRESS        = "AWAITING_ADDRESS";
    private static final String STATE_AWAITING_MORE_ADDRESSES = "AWAITING_MORE_ADDRESSES";
    private static final String STATE_REGISTERED              = "REGISTERED";

    private static final DateTimeFormatter BIRTHDAY_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private static final List<Map<String, String>> MORE_ADDRESS_QUICK_REPLIES = List.of(
            Map.of("title", "Add another", "payload", "ADD_ADDRESS"),
            Map.of("title", "Done",         "payload", "DONE")
    );

    private final InstagramBotConfigRepository configRepository;
    private final InstagramSubscriberRepository subscriberRepository;
    private final InstagramSubscriberAddressRepository addressRepository;
    private final CustomerRepository customerRepository;
    private final InstagramApiClient apiClient;

    // -------------------------------------------------------------------------
    // Incoming message handler (called from webhook service)
    // -------------------------------------------------------------------------

    @Transactional
    public void handleIncomingMessage(String senderIgsid, String username,
                                      String text, String quickReplyPayload) {
        InstagramBotConfig config = getActiveConfig();
        if (config == null) return;

        // Always (re-)start wizard on "hi" / "start" keywords or if subscriber is new
        Optional<InstagramSubscriber> existing = subscriberRepository.findByIgsid(senderIgsid);

        if (existing.isEmpty() || isRestartKeyword(text)) {
            startRegistration(config, senderIgsid, username);
            return;
        }

        InstagramSubscriber subscriber = existing.get();
        subscriber.touch();

        String state = subscriber.getConversationState();
        if (state == null) state = STATE_AWAITING_NAME;

        // Quick-reply button took priority
        if (quickReplyPayload != null) {
            handleQuickReply(config, subscriber, quickReplyPayload);
            return;
        }

        switch (state) {
            case STATE_AWAITING_NAME           -> handleNameInput(config, subscriber, text);
            case STATE_AWAITING_PHONE          -> handlePhoneInput(config, subscriber, text);
            case STATE_AWAITING_BIRTHDAY       -> handleBirthdayInput(config, subscriber, text);
            case STATE_AWAITING_ADDRESS        -> handleAddressInput(config, subscriber, text);
            case STATE_AWAITING_MORE_ADDRESSES -> {
                // User typed instead of using quick-reply buttons → treat as another address
                handleAddressInput(config, subscriber, text);
            }
            case STATE_REGISTERED              -> sendMainMenu(config, subscriber);
            default                            -> startRegistration(config, senderIgsid, username);
        }
    }

    // -------------------------------------------------------------------------
    // Wizard steps
    // -------------------------------------------------------------------------

    private void startRegistration(InstagramBotConfig config, String igsid, String username) {
        InstagramSubscriber subscriber = subscriberRepository.findByIgsid(igsid)
                .orElse(InstagramSubscriber.builder()
                        .igsid(igsid)
                        .subscribedAt(OffsetDateTime.now(ZoneOffset.UTC))
                        .isActive(true)
                        .isBlocked(false)
                        .build());

        subscriber.setUsername(username);
        subscriber.setConversationState(STATE_AWAITING_NAME);
        subscriber.touch();
        subscriberRepository.save(subscriber);

        String welcome = (config.getWelcomeMessage() != null && !config.getWelcomeMessage().isBlank())
                ? config.getWelcomeMessage() + "\n\n"
                : "👋 Xush kelibsiz " + brandName + "'ga!\n\n";
        apiClient.sendMessage(config, igsid, welcome + "Ismingizni kiriting (to'liq ism yoki laqab):");
    }

    private void handleNameInput(InstagramBotConfig config, InstagramSubscriber subscriber, String text) {
        String name = text.trim();
        if (name.length() < 2 || name.length() > 100) {
            send(config, subscriber, "Iltimos, to'liq ismingizni kiriting (2–100 belgi):");
            return;
        }
        subscriber.setDisplayName(name);
        subscriber.setConversationState(STATE_AWAITING_PHONE);
        subscriberRepository.save(subscriber);
        send(config, subscriber,
                "Juda yaxshi, " + esc(name) + "! 😊\n\nTelefon raqamingizni kiriting (masalan: +998901234567):");
    }

    private void handlePhoneInput(InstagramBotConfig config, InstagramSubscriber subscriber, String text) {
        String phone = normalizePhone(text.trim());
        if (phone.replaceAll("[^\\d]", "").length() < 7) {
            send(config, subscriber, "❌ To'g'ri telefon raqam kiriting (masalan: +998901234567):");
            return;
        }
        subscriber.setPhone(phone);
        subscriber.setConversationState(STATE_AWAITING_BIRTHDAY);
        subscriberRepository.save(subscriber);
        send(config, subscriber,
                "📅 Tug'ilgan kuningizni kiriting (DD.MM.YYYY, masalan: 15.03.1990)\n\nO'tkazib yuborish uchun: /skip");
    }

    private void handleBirthdayInput(InstagramBotConfig config, InstagramSubscriber subscriber, String text) {
        if (!text.trim().equals("/skip")) {
            try {
                subscriber.setBirthDate(LocalDate.parse(text.trim(), BIRTHDAY_FMT));
            } catch (DateTimeParseException e) {
                send(config, subscriber,
                        "❌ Format noto'g'ri. DD.MM.YYYY ko'rinishida kiriting yoki /skip yozing:");
                return;
            }
        }
        subscriber.setConversationState(STATE_AWAITING_ADDRESS);
        subscriberRepository.save(subscriber);
        send(config, subscriber,
                "📍 Yetkazib berish manzilingizni yozing (ko'cha, uy raqami, mo'ljal):\n\nO'tkazib yuborish: /skip");
    }

    private void handleAddressInput(InstagramBotConfig config, InstagramSubscriber subscriber, String text) {
        if (text.trim().equals("/skip")) {
            completeRegistration(config, subscriber);
            return;
        }
        long existing = addressRepository.countBySubscriber(subscriber);
        InstagramSubscriberAddress addr = InstagramSubscriberAddress.builder()
                .subscriber(subscriber)
                .address(text.trim())
                .isDefault(existing == 0)
                .build();
        addressRepository.save(addr);

        subscriber.setConversationState(STATE_AWAITING_MORE_ADDRESSES);
        subscriberRepository.save(subscriber);

        apiClient.sendMessageWithQuickReplies(config, subscriber.getIgsid(),
                "✅ Manzil saqlandi! (Jami: " + (existing + 1) + " ta)\n\nYana manzil qo'shmoqchimisiz?",
                MORE_ADDRESS_QUICK_REPLIES);
    }

    private void handleQuickReply(InstagramBotConfig config, InstagramSubscriber subscriber, String payload) {
        switch (payload) {
            case "ADD_ADDRESS" -> {
                subscriber.setConversationState(STATE_AWAITING_ADDRESS);
                subscriberRepository.save(subscriber);
                send(config, subscriber, "📍 Yangi manzilni yozing:");
            }
            case "DONE" -> completeRegistration(config, subscriber);
            default     -> sendMainMenu(config, subscriber);
        }
    }

    private void completeRegistration(InstagramBotConfig config, InstagramSubscriber subscriber) {
        // Try to link to existing customer by phone
        if (subscriber.getPhone() != null && subscriber.getCustomer() == null) {
            // V150: the Instagram bot is a global channel with no restaurant context, so link to the
            // customer's primary record (oldest row for the phone).
            customerRepository.findFirstByPhoneOrderByIdAsc(subscriber.getPhone()).ifPresent(customer -> {
                subscriber.setCustomer(customer);
                log.info("Linked Instagram subscriber {} to customer {}", subscriber.getIgsid(), customer.getId());
            });
        }
        subscriber.setConversationState(STATE_REGISTERED);
        subscriberRepository.save(subscriber);
        log.info("Instagram subscriber registered: igsid={}", subscriber.getIgsid());

        List<InstagramSubscriberAddress> addresses = addressRepository.findAllBySubscriber(subscriber);

        StringBuilder sb = new StringBuilder();
        sb.append("🎉 Ro'yxatdan o'tish yakunlandi!\n\n");
        sb.append("📋 Sizning ma'lumotlaringiz:\n");
        sb.append("👤 ").append(esc(subscriber.getDisplayNameOrFallback())).append("\n");
        sb.append("📱 ").append(subscriber.getPhone() != null ? subscriber.getPhone() : "kiritilmagan").append("\n");
        sb.append("🎂 ").append(subscriber.getBirthDate() != null
                ? subscriber.getBirthDate().format(BIRTHDAY_FMT) : "kiritilmagan").append("\n");
        if (!addresses.isEmpty()) {
            sb.append("📍 Manzillar: ").append(addresses.size()).append(" ta\n");
        }
        if (subscriber.getCustomer() != null) {
            sb.append("\n✨ Hisobingiz mijoz profili bilan bog'landi!\n");
        }
        sb.append("\n🎁 Endi siz aksiyalar va tug'ilgan kun sovg'alari haqida xabar olasiz!");
        send(config, subscriber, sb.toString());
    }

    private void sendMainMenu(InstagramBotConfig config, InstagramSubscriber subscriber) {
        send(config, subscriber,
                "👋 Salom, " + esc(subscriber.getDisplayNameOrFallback()) + "!\n\n" +
                "Ma'lumotlarni yangilash uchun \"hi\" yoki \"start\" yozing.");
    }

    // -------------------------------------------------------------------------
    // Public outbound API
    // -------------------------------------------------------------------------

    public boolean sendMessage(String igsid, String text) {
        InstagramBotConfig config = getActiveConfig();
        if (config == null) {
            log.debug("No active Instagram config, skipping message to {}", igsid);
            return false;
        }
        return apiClient.sendMessage(config, igsid, text);
    }

    // -------------------------------------------------------------------------
    // Admin DM controls
    // -------------------------------------------------------------------------

    @Transactional
    public InstagramSubscriber blockSubscriber(Long id) {
        InstagramSubscriber s = subscriberRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Subscriber not found: " + id));
        s.setIsBlocked(true);
        return subscriberRepository.save(s);
    }

    @Transactional
    public InstagramSubscriber unblockSubscriber(Long id) {
        InstagramSubscriber s = subscriberRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Subscriber not found: " + id));
        s.setIsBlocked(false);
        return subscriberRepository.save(s);
    }

    /**
     * Admin sends a DM to one subscriber by their DB id.
     * Returns true if the Meta API accepted the message.
     */
    public boolean sendAdminMessage(Long id, String text) {
        InstagramSubscriber s = subscriberRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Subscriber not found: " + id));
        InstagramBotConfig config = getActiveConfig();
        if (config == null) {
            log.warn("No active Instagram config — cannot send admin DM to subscriber {}", id);
            return false;
        }
        return apiClient.sendMessage(config, s.getIgsid(), text);
    }

    /**
     * Broadcast a text message to multiple subscribers.
     *
     * @param text    message to send
     * @param target  "ALL" = all active non-blocked; "REGISTERED" = only fully registered ones
     * @return number of messages successfully delivered
     */
    public int broadcast(String text, String target) {
        InstagramBotConfig config = getActiveConfig();
        if (config == null) {
            log.warn("No active Instagram config — broadcast skipped");
            return 0;
        }
        List<InstagramSubscriber> recipients = "REGISTERED".equalsIgnoreCase(target)
                ? subscriberRepository.findAllRegistered()
                : subscriberRepository.findAllActiveNotBlocked();

        int sent = 0;
        for (InstagramSubscriber s : recipients) {
            try {
                if (apiClient.sendMessage(config, s.getIgsid(), text)) {
                    sent++;
                }
            } catch (Exception e) {
                log.error("Broadcast failed for subscriber {}: {}", s.getId(), e.getMessage());
            }
        }
        log.info("Instagram broadcast sent to {}/{} recipients", sent, recipients.size());
        return sent;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    public InstagramBotConfig getActiveConfig() {
        return configRepository.findByIsActiveTrue().orElse(null);
    }

    private void send(InstagramBotConfig config, InstagramSubscriber subscriber, String text) {
        apiClient.sendMessage(config, subscriber.getIgsid(), text);
    }

    private boolean isRestartKeyword(String text) {
        if (text == null) return false;
        String t = text.trim().toLowerCase();
        return t.equals("start") || t.equals("hi") || t.equals("hello")
                || t.equals("boshlash") || t.equals("/start");
    }

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String d = phone.replaceAll("[^\\d+]", "");
        return d.startsWith("+") ? d : "+" + d;
    }

    private String esc(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
