package com.elcafe.modules.settings.service;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.settings.entity.PrinterSettings;
import com.elcafe.modules.settings.repository.PrinterSettingsRepository;
import com.github.anastaciocintra.escpos.EscPos;
import com.github.anastaciocintra.escpos.EscPosConst;
import com.github.anastaciocintra.escpos.Style;
import com.github.anastaciocintra.output.PrinterOutputStream;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.print.PrintServiceLookup;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrintService {

    private final PrinterSettingsRepository printerSettingsRepository;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Print kitchen order to thermal printer
     */
    public void printKitchenOrder(Order order) {
        try {
            log.info("Printing kitchen order: {}", order.getOrderNumber());

            Optional<PrinterSettings> printerSettings = printerSettingsRepository
                    .findByRestaurant_IdAndPrinterTypeAndEnabled(
                            order.getRestaurant().getId(),
                            PrinterSettings.PrinterType.KITCHEN,
                            true
                    );

            if (printerSettings.isEmpty()) {
                log.warn("No enabled kitchen printer found for restaurant: {}", order.getRestaurant().getId());
                return;
            }

            PrinterSettings settings = printerSettings.get();

            if ("NETWORK".equalsIgnoreCase(settings.getConnectionType())) {
                printToNetworkPrinter(order, settings);
            } else {
                printToUSBPrinter(order, settings);
            }

            log.info("Kitchen order printed successfully: {}", order.getOrderNumber());
        } catch (Exception e) {
            log.error("Failed to print kitchen order: {}", order.getOrderNumber(), e);
            // Don't throw exception - printing failure shouldn't block order creation
        }
    }

    /**
     * Generate PDF for kitchen order
     */
    public byte[] generateKitchenOrderPDF(Order order) {
        try {
            String html = generateKitchenOrderHTML(order);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(outputStream);
            builder.run();

            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate kitchen order PDF", e);
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }

    /**
     * Save kitchen order PDF to file
     */
    public void saveKitchenOrderPDF(Order order, String filePath) {
        try {
            byte[] pdfBytes = generateKitchenOrderPDF(order);
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                fos.write(pdfBytes);
            }
            log.info("Kitchen order PDF saved: {}", filePath);
        } catch (Exception e) {
            log.error("Failed to save kitchen order PDF", e);
            throw new RuntimeException("Failed to save PDF", e);
        }
    }

    /**
     * Print to USB thermal printer
     */
    private void printToUSBPrinter(Order order, PrinterSettings settings) {
        try {
            javax.print.PrintService printService = findPrintService(settings.getPrinterName());
            if (printService == null) {
                log.error("Printer not found: {}", settings.getPrinterName());
                return;
            }

            PrinterOutputStream printerOutputStream = new PrinterOutputStream(printService);
            EscPos escpos = new EscPos(printerOutputStream);

            printKitchenOrderContent(escpos, order, settings);

            escpos.feed(3);
            escpos.cut(EscPos.CutMode.FULL);
            escpos.close();

        } catch (Exception e) {
            log.error("Failed to print to USB printer", e);
        }
    }

    /**
     * Print to network thermal printer
     */
    private void printToNetworkPrinter(Order order, PrinterSettings settings) {
        log.warn("Network printer support not yet implemented. Use USB printer instead.");
        // TODO: Implement network printer support
        // Socket socket = new Socket(settings.getIpAddress(), settings.getPort());
        // EscPos escpos = new EscPos(socket.getOutputStream());
    }

    /**
     * Print kitchen order content to thermal printer
     */
    private void printKitchenOrderContent(EscPos escpos, Order order, PrinterSettings settings) throws IOException {
        Style titleStyle = new Style()
                .setFontSize(Style.FontSize._2, Style.FontSize._2)
                .setBold(true)
                .setJustification(EscPosConst.Justification.Center);

        Style headerStyle = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._1)
                .setBold(true);

        Style normalStyle = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._1);

        // Header
        escpos.writeLF(titleStyle, "*** OSHXONA BUYURTMASI ***");
        escpos.feed(1);

        // Order details
        escpos.writeLF(headerStyle, "BUYURTMA #" + order.getOrderNumber());
        escpos.writeLF(normalStyle, order.getRestaurant().getName());
        escpos.writeLF(normalStyle, DATE_TIME_FORMATTER.format(order.getCreatedAt()));
        escpos.feed(1);

        // Order type and table
        if (order.getOrderType() != null) {
            escpos.writeLF(normalStyle, "Turi: " + order.getOrderType().toString().replace("_", " "));
        }

        if (order.getDiningTable() != null) {
            escpos.writeLF(headerStyle, "STOL: " + order.getDiningTable().getTableNumber());
            if (order.getDiningTable().getSection() != null) {
                escpos.writeLF(normalStyle, "Bo'lim: " + order.getDiningTable().getSection());
            }
        }

        escpos.feed(1);
        escpos.writeLF("================================");

        // Items
        escpos.writeLF(headerStyle, "MAHSULOTLAR:");
        escpos.feed(1);

        for (var item : order.getItems()) {
            Style itemStyle = new Style()
                    .setFontSize(Style.FontSize._1, Style.FontSize._1)
                    .setBold(true);

            String itemLine = String.format("%dx %s",
                    item.getQuantity(),
                    item.getProductName()
            );
            escpos.writeLF(itemStyle, itemLine);

            if (item.getVariantName() != null && !item.getVariantName().isEmpty()) {
                escpos.writeLF(normalStyle, "  (" + item.getVariantName() + ")");
            }

            if (item.getSpecialInstructions() != null && !item.getSpecialInstructions().isEmpty()) {
                escpos.writeLF(normalStyle, "  Maxsus: " + item.getSpecialInstructions());
            }

            escpos.feed(1);
        }

        escpos.writeLF("================================");

        // Customer notes
        if (order.getCustomerNotes() != null && !order.getCustomerNotes().isEmpty()) {
            escpos.feed(1);
            escpos.writeLF(headerStyle, "MAXSUS ESLATMALAR:");
            escpos.writeLF(normalStyle, order.getCustomerNotes());
            escpos.feed(1);
            escpos.writeLF("================================");
        }

        // Delivery info
        if (order.getDeliveryInfo() != null) {
            escpos.feed(1);
            escpos.writeLF(headerStyle, "YETKAZIB BERISH:");
            escpos.writeLF(normalStyle, "Ism: " + (order.getDeliveryInfo().getContactName() != null ? order.getDeliveryInfo().getContactName() : "N/A"));
            escpos.writeLF(normalStyle, "Telefon: " + (order.getDeliveryInfo().getContactPhone() != null ? order.getDeliveryInfo().getContactPhone() : "N/A"));
            if (order.getDeliveryInfo().getAddress() != null) {
                escpos.writeLF(normalStyle, "Manzil: " + order.getDeliveryInfo().getAddress());
            }
            escpos.writeLF("================================");
        }

        escpos.feed(1);
        escpos.writeLF(titleStyle, "HOZIR TAYYORLANG!");
        escpos.feed(1);
    }

    /**
     * Generate HTML content for kitchen order
     */
    private String generateKitchenOrderHTML(Order order) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>");
        html.append("<html>");
        html.append("<head>");
        html.append("<meta charset='UTF-8'/>");
        html.append("<style>");
        html.append("* { margin: 0; padding: 0; box-sizing: border-box; }");
        html.append("@page { size: 80mm auto; margin: 5mm; }");
        html.append("body { font-family: Arial, sans-serif; font-size: 14px; width: 80mm; }");
        html.append(".header { text-align: center; font-size: 24px; font-weight: bold; margin-bottom: 10px; border-bottom: 3px solid black; padding-bottom: 10px; }");
        html.append(".section { margin: 10px 0; padding: 10px; border: 2px solid black; }");
        html.append(".section-title { font-size: 18px; font-weight: bold; margin-bottom: 5px; }");
        html.append(".item { margin: 10px 0; padding: 10px; background: #f0f0f0; border: 2px solid black; }");
        html.append(".item-name { font-size: 16px; font-weight: bold; }");
        html.append(".item-quantity { font-size: 20px; font-weight: bold; margin-right: 10px; }");
        html.append(".notes { font-style: italic; margin-top: 5px; background: #fff3cd; padding: 5px; }");
        html.append(".footer { text-align: center; font-size: 20px; font-weight: bold; margin-top: 20px; padding: 10px; border: 3px solid black; }");
        html.append("</style>");
        html.append("</head>");
        html.append("<body>");

        // Header
        html.append("<div class='header'>*** OSHXONA BUYURTMASI ***</div>");

        // Order info
        html.append("<div class='section'>");
        html.append("<div class='section-title'>BUYURTMA #").append(order.getOrderNumber()).append("</div>");
        html.append("<div>").append(order.getRestaurant().getName()).append("</div>");
        html.append("<div>").append(DATE_TIME_FORMATTER.format(order.getCreatedAt())).append("</div>");
        if (order.getOrderType() != null) {
            html.append("<div>Turi: ").append(order.getOrderType().toString().replace("_", " ")).append("</div>");
        }
        if (order.getDiningTable() != null) {
            html.append("<div style='font-size: 20px; font-weight: bold; margin-top: 5px;'>STOL: ")
                    .append(order.getDiningTable().getTableNumber()).append("</div>");
        }
        html.append("</div>");

        // Items
        html.append("<div class='section'>");
        html.append("<div class='section-title'>MAHSULOTLAR:</div>");
        for (var item : order.getItems()) {
            html.append("<div class='item'>");
            html.append("<span class='item-quantity'>").append(item.getQuantity()).append("x</span>");
            html.append("<span class='item-name'>").append(item.getProductName()).append("</span>");
            if (item.getVariantName() != null && !item.getVariantName().isEmpty()) {
                html.append("<div>(").append(item.getVariantName()).append(")</div>");
            }
            if (item.getSpecialInstructions() != null && !item.getSpecialInstructions().isEmpty()) {
                html.append("<div class='notes'>Maxsus: ").append(item.getSpecialInstructions()).append("</div>");
            }
            html.append("</div>");
        }
        html.append("</div>");

        // Customer notes
        if (order.getCustomerNotes() != null && !order.getCustomerNotes().isEmpty()) {
            html.append("<div class='section'>");
            html.append("<div class='section-title'>MAXSUS ESLATMALAR:</div>");
            html.append("<div class='notes'>").append(order.getCustomerNotes()).append("</div>");
            html.append("</div>");
        }

        // Delivery info
        if (order.getDeliveryInfo() != null) {
            html.append("<div class='section'>");
            html.append("<div class='section-title'>YETKAZIB BERISH MA'LUMOTLARI:</div>");
            html.append("<div>Ism: ").append(order.getDeliveryInfo().getContactName() != null ? order.getDeliveryInfo().getContactName() : "N/A").append("</div>");
            html.append("<div>Telefon: ").append(order.getDeliveryInfo().getContactPhone() != null ? order.getDeliveryInfo().getContactPhone() : "N/A").append("</div>");
            if (order.getDeliveryInfo().getAddress() != null) {
                html.append("<div>Manzil: ").append(order.getDeliveryInfo().getAddress()).append("</div>");
            }
            html.append("</div>");
        }

        html.append("<div class='footer'>HOZIR TAYYORLANG!</div>");

        html.append("</body>");
        html.append("</html>");

        return html.toString();
    }

    /**
     * Find system print service by name
     */
    private javax.print.PrintService findPrintService(String printerName) {
        javax.print.PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null, null);
        for (javax.print.PrintService printService : printServices) {
            if (printService.getName().equalsIgnoreCase(printerName)) {
                return printService;
            }
        }
        return null;
    }

    /**
     * Get all available printers
     */
    public String[] getAvailablePrinters() {
        javax.print.PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null, null);
        String[] printerNames = new String[printServices.length];
        for (int i = 0; i < printServices.length; i++) {
            printerNames[i] = printServices[i].getName();
        }
        return printerNames;
    }

    /**
     * Test printer connection
     */
    public boolean testPrinter(Long printerSettingsId) {
        try {
            PrinterSettings settings = printerSettingsRepository.findById(printerSettingsId)
                    .orElseThrow(() -> new RuntimeException("Printer settings not found"));

            javax.print.PrintService printService = findPrintService(settings.getPrinterName());
            return printService != null;
        } catch (Exception e) {
            log.error("Failed to test printer", e);
            return false;
        }
    }
}
