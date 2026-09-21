package com.elcafe.modules.ownerbot.enums;

public enum OwnerNotificationType {
    NEW_ORDER("🆕 Новый заказ", "Yangi buyurtma"),
    NEW_RESERVATION("📅 Новая бронь", "Yangi band qilish"),
    LOW_STOCK("📦 Мало на складе", "Kam qoldi"),
    CUSTOMER_REVIEW("⭐ Отзыв клиента", "Mijoz sharhi"),
    DAILY_REPORT("📊 Дневной отчёт", "Kunlik hisobot"),
    CRITICAL_ALERT("🚨 Критическое", "Muhim ogohlantirish"),
    ORDER_CANCELLED("❌ Заказ отменён", "Buyurtma bekor qilindi"),
    RESERVATION_CANCELLED("❌ Бронь отменена", "Band qilish bekor qilindi"),
    ORDER_READY("✅ Заказ готов", "Buyurtma tayyor"),
    SYSTEM_ALERT("⚠️ Системное", "Tizim ogohlantirishlari");

    private final String titleRu;
    private final String titleUz;

    OwnerNotificationType(String titleRu, String titleUz) {
        this.titleRu = titleRu;
        this.titleUz = titleUz;
    }

    public String getTitle(String lang) {
        return "uz".equalsIgnoreCase(lang) ? titleUz : titleRu;
    }

    public String getTitleRu() {
        return titleRu;
    }

    public String getTitleUz() {
        return titleUz;
    }

    public String getEmoji() {
        return switch (this) {
            case NEW_ORDER -> "🆕";
            case NEW_RESERVATION -> "📅";
            case LOW_STOCK -> "📦";
            case CUSTOMER_REVIEW -> "⭐";
            case DAILY_REPORT -> "📊";
            case CRITICAL_ALERT -> "🚨";
            case ORDER_CANCELLED, RESERVATION_CANCELLED -> "❌";
            case ORDER_READY -> "✅";
            case SYSTEM_ALERT -> "⚠️";
        };
    }
}
