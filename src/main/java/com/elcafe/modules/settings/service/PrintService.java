package com.elcafe.modules.settings.service;

import com.elcafe.modules.kitchen.entity.KitchenStation;
import com.elcafe.modules.kitchen.repository.KitchenStationRepository;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.CategoryRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.settings.entity.PrinterSettings;
import com.elcafe.modules.settings.repository.PrinterSettingsRepository;
import com.elcafe.modules.settings.websocket.PrintAgentWebSocketHandler;
import com.github.anastaciocintra.escpos.EscPos;
import com.github.anastaciocintra.escpos.EscPosConst;
import com.github.anastaciocintra.escpos.Style;
import com.github.anastaciocintra.output.PrinterOutputStream;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.print.PrintServiceLookup;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrintService {

    private final PrinterSettingsRepository printerSettingsRepository;
    private final KitchenStationRepository kitchenStationRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    @Lazy
    private final PrintJobService printJobService;
    @Lazy
    private final PrintAgentWebSocketHandler printAgentHandler;

    // Set to true to use print agent queue, false for direct printing
    @Value("${app.printing.use-agent:true}")
    private boolean usePrintAgent;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Print kitchen order to thermal printer(s)
     * Routes items to appropriate station printers based on category assignments
     */
    public void printKitchenOrder(Order order) {
        try {
            log.info("Printing kitchen order: {}", order.getOrderNumber());

            Long restaurantId = order.getRestaurant().getId();

            // Get active kitchen stations with printers
            List<KitchenStation> stations = kitchenStationRepository.findActiveStationsWithPrinters(restaurantId);

            if (stations.isEmpty()) {
                // No stations configured - use legacy single printer method
                printKitchenOrderLegacy(order);
                return;
            }

            // Group items by station
            Map<KitchenStation, List<OrderItem>> itemsByStation = groupItemsByStation(order.getItems(), stations);

            // Print to each station's printer
            for (Map.Entry<KitchenStation, List<OrderItem>> entry : itemsByStation.entrySet()) {
                KitchenStation station = entry.getKey();
                List<OrderItem> stationItems = entry.getValue();

                if (stationItems.isEmpty()) {
                    continue;
                }

                if (station == null) {
                    // Items without station assignment - print to default kitchen printer
                    printItemsToDefaultPrinter(order, stationItems);
                } else if (station.getPrinter() != null && station.getPrinter().getEnabled()) {
                    // Print station-specific ticket
                    printStationTicket(order, station, stationItems);
                } else {
                    log.warn("Station {} has no enabled printer configured, printing to default", station.getName());
                    printItemsToDefaultPrinter(order, stationItems);
                }
            }

            log.info("Kitchen order printed successfully: {}", order.getOrderNumber());
        } catch (Exception e) {
            log.error("Failed to print kitchen order: {}", order.getOrderNumber(), e);
            // Don't throw exception - printing failure shouldn't block order creation
        }
    }

    /**
     * Group order items by their kitchen station based on product category
     */
    private Map<KitchenStation, List<OrderItem>> groupItemsByStation(List<OrderItem> items, List<KitchenStation> stations) {
        Map<KitchenStation, List<OrderItem>> result = new LinkedHashMap<>();

        // Initialize with null key for items without station
        result.put(null, new ArrayList<>());

        // Initialize for each station
        for (KitchenStation station : stations) {
            result.put(station, new ArrayList<>());
        }

        // Get all product IDs
        Set<Long> productIds = items.stream()
                .map(OrderItem::getProductId)
                .collect(Collectors.toSet());

        // Load products with categories
        Map<Long, Product> productMap = productRepository.findAllById(productIds)
                .stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        // Get category IDs
        Set<Long> categoryIds = productMap.values().stream()
                .filter(p -> p.getCategory() != null)
                .map(p -> p.getCategory().getId())
                .collect(Collectors.toSet());

        // Load categories with kitchen stations
        Map<Long, Category> categoryMap = categoryRepository.findAllById(categoryIds)
                .stream()
                .collect(Collectors.toMap(Category::getId, c -> c));

        // Group items
        for (OrderItem item : items) {
            Product product = productMap.get(item.getProductId());
            KitchenStation itemStation = null;

            if (product != null && product.getCategory() != null) {
                Category category = categoryMap.get(product.getCategory().getId());
                if (category != null && category.getKitchenStation() != null) {
                    // Find matching station from our loaded stations
                    Long stationId = category.getKitchenStation().getId();
                    itemStation = stations.stream()
                            .filter(s -> s.getId().equals(stationId))
                            .findFirst()
                            .orElse(null);
                }
            }

            result.get(itemStation).add(item);
        }

        return result;
    }

    /**
     * Print station-specific ticket
     * Uses print agent queue if enabled, otherwise prints directly
     */
    private void printStationTicket(Order order, KitchenStation station, List<OrderItem> items) {
        try {
            PrinterSettings settings = station.getPrinter();
            log.info("Printing {} items to station: {} (printer: {})",
                    items.size(), station.getName(), settings.getPrinterName());

            // Use print agent queue if enabled
            if (usePrintAgent) {
                printJobService.createStationPrintJob(order, station, items, settings);
                return;
            }

            // Direct printing (backend on same network as printer)
            if ("NETWORK".equalsIgnoreCase(settings.getConnectionType())) {
                printStationToNetworkPrinter(order, station, items, settings);
            } else {
                printStationToUSBPrinter(order, station, items, settings);
            }
        } catch (Exception e) {
            log.error("Failed to print to station: {}", station.getName(), e);
        }
    }

    /**
     * Print items to default kitchen printer (for items without station assignment)
     * Uses print agent queue if enabled, otherwise prints directly
     */
    private void printItemsToDefaultPrinter(Order order, List<OrderItem> items) {
        try {
            Optional<PrinterSettings> printerSettings = printerSettingsRepository
                    .findByRestaurant_IdAndPrinterTypeAndEnabled(
                            order.getRestaurant().getId(),
                            PrinterSettings.PrinterType.KITCHEN,
                            true
                    );

            if (printerSettings.isEmpty()) {
                log.warn("No default kitchen printer found for unassigned items");
                return;
            }

            PrinterSettings settings = printerSettings.get();
            log.info("Printing {} unassigned items to default printer: {}",
                    items.size(), settings.getPrinterName());

            // Use print agent queue if enabled
            if (usePrintAgent) {
                printJobService.createStationPrintJob(order, null, items, settings);
                return;
            }

            // Direct printing
            if ("NETWORK".equalsIgnoreCase(settings.getConnectionType())) {
                printStationToNetworkPrinter(order, null, items, settings);
            } else {
                printStationToUSBPrinter(order, null, items, settings);
            }
        } catch (Exception e) {
            log.error("Failed to print to default printer", e);
        }
    }

    /**
     * Print station ticket to USB printer
     */
    private void printStationToUSBPrinter(Order order, KitchenStation station, List<OrderItem> items, PrinterSettings settings) {
        try {
            javax.print.PrintService printService = findPrintService(settings.getPrinterName());
            if (printService == null) {
                log.error("Printer not found: {}", settings.getPrinterName());
                return;
            }

            PrinterOutputStream printerOutputStream = new PrinterOutputStream(printService);
            EscPos escpos = new EscPos(printerOutputStream);

            printStationTicketContent(escpos, order, station, items, settings);

            escpos.feed(3);
            escpos.cut(EscPos.CutMode.FULL);
            escpos.close();

        } catch (Exception e) {
            log.error("Failed to print station ticket to USB printer", e);
        }
    }

    /**
     * Print station ticket to network printer
     */
    private void printStationToNetworkPrinter(Order order, KitchenStation station, List<OrderItem> items, PrinterSettings settings) {
        String ipAddress = settings.getIpAddress();
        Integer port = settings.getPort() != null ? settings.getPort() : 9100;

        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            log.error("Network printer IP address not configured for printer: {}", settings.getPrinterName());
            return;
        }

        try (Socket socket = new Socket()) {
            // Connect with timeout
            socket.connect(new InetSocketAddress(ipAddress, port), 5000);
            socket.setSoTimeout(10000);

            OutputStream outputStream = socket.getOutputStream();
            EscPos escpos = new EscPos(outputStream);

            printStationTicketContent(escpos, order, station, items, settings);

            escpos.feed(3);
            escpos.cut(EscPos.CutMode.FULL);
            escpos.close();

            log.info("Successfully printed to network printer: {}:{}", ipAddress, port);
        } catch (Exception e) {
            log.error("Failed to print to network printer {}:{} - {}", ipAddress, port, e.getMessage(), e);
        }
    }

    /**
     * Print station ticket content
     */
    private void printStationTicketContent(EscPos escpos, Order order, KitchenStation station, List<OrderItem> items, PrinterSettings settings) throws IOException {
        Style titleStyle = new Style()
                .setFontSize(Style.FontSize._2, Style.FontSize._2)
                .setBold(true)
                .setJustification(EscPosConst.Justification.Center);

        Style stationStyle = new Style()
                .setFontSize(Style.FontSize._2, Style.FontSize._2)
                .setBold(true)
                .setJustification(EscPosConst.Justification.Center);

        Style headerStyle = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._1)
                .setBold(true);

        Style normalStyle = new Style()
                .setFontSize(Style.FontSize._1, Style.FontSize._1);

        // Station Header
        if (station != null) {
            escpos.writeLF(stationStyle, "*** " + station.getName().toUpperCase() + " ***");
        } else {
            escpos.writeLF(titleStyle, "*** OSHXONA BUYURTMASI ***");
        }
        escpos.feed(1);

        // Order details
        escpos.writeLF(headerStyle, "BUYURTMA #" + order.getOrderNumber());
        escpos.writeLF(normalStyle, DATE_TIME_FORMATTER.format(order.getCreatedAt()));
        escpos.feed(1);

        // Order type and table
        if (order.getOrderType() != null) {
            escpos.writeLF(normalStyle, "Turi: " + order.getOrderType().toString().replace("_", " "));
        }

        String tableInfo = getTableNumberFromOrder(order);
        if (tableInfo != null && !tableInfo.isEmpty()) {
            escpos.writeLF(headerStyle, "STOL: " + tableInfo);
        }

        escpos.feed(1);
        escpos.writeLF("================================");

        // Items for this station
        escpos.writeLF(headerStyle, "MAHSULOTLAR (" + items.size() + "):");
        escpos.feed(1);

        for (OrderItem item : items) {
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

        // Customer notes (only on first ticket or if relevant)
        if (order.getCustomerNotes() != null && !order.getCustomerNotes().isEmpty()) {
            escpos.feed(1);
            escpos.writeLF(headerStyle, "ESLATMALAR:");
            escpos.writeLF(normalStyle, order.getCustomerNotes());
            escpos.writeLF("================================");
        }

        escpos.feed(1);
        escpos.writeLF(titleStyle, "HOZIR TAYYORLANG!");
        escpos.feed(1);
    }

    /**
     * Legacy method: Print kitchen order to single thermal printer (no station routing)
     * Uses print agent queue if enabled, otherwise prints directly
     */
    private void printKitchenOrderLegacy(Order order) {
        try {
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

            // Use print agent queue if enabled
            if (usePrintAgent) {
                printJobService.createLegacyPrintJob(order, settings);
                return;
            }

            // Direct printing
            if ("NETWORK".equalsIgnoreCase(settings.getConnectionType())) {
                printToNetworkPrinter(order, settings);
            } else {
                printToUSBPrinter(order, settings);
            }
        } catch (Exception e) {
            log.error("Failed to print kitchen order (legacy): {}", order.getOrderNumber(), e);
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
     * Print to USB thermal printer (legacy)
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
     * Print to network thermal printer (legacy)
     */
    private void printToNetworkPrinter(Order order, PrinterSettings settings) {
        String ipAddress = settings.getIpAddress();
        Integer port = settings.getPort() != null ? settings.getPort() : 9100;

        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            log.error("Network printer IP address not configured for printer: {}", settings.getPrinterName());
            return;
        }

        try (Socket socket = new Socket()) {
            // Connect with timeout
            socket.connect(new InetSocketAddress(ipAddress, port), 5000);
            socket.setSoTimeout(10000);

            OutputStream outputStream = socket.getOutputStream();
            EscPos escpos = new EscPos(outputStream);

            printKitchenOrderContent(escpos, order, settings);

            escpos.feed(3);
            escpos.cut(EscPos.CutMode.FULL);
            escpos.close();

            log.info("Successfully printed to network printer: {}:{}", ipAddress, port);
        } catch (Exception e) {
            log.error("Failed to print to network printer {}:{} - {}", ipAddress, port, e.getMessage(), e);
        }
    }

    /**
     * Print kitchen order content to thermal printer (legacy)
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

        // Print table information - check both diningTable and tableIds
        String tableInfo = getTableNumberFromOrder(order);
        if (tableInfo != null && !tableInfo.isEmpty()) {
            escpos.writeLF(headerStyle, "STOL: " + tableInfo);
            if (order.getDiningTable() != null && order.getDiningTable().getSection() != null) {
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
        // Print table information - check both diningTable and tableIds
        String tableInfo = getTableNumberFromOrder(order);
        if (tableInfo != null && !tableInfo.isEmpty()) {
            html.append("<div style='font-size: 20px; font-weight: bold; margin-top: 5px;'>STOL: ")
                    .append(tableInfo).append("</div>");
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
     * Get table number from order
     * Handles both diningTable and tableIds for multi-table orders
     */
    private String getTableNumberFromOrder(Order order) {
        // First, try to get from diningTable (single table)
        if (order.getDiningTable() != null && order.getDiningTable().getTableNumber() != null) {
            return order.getDiningTable().getTableNumber();
        }
        // Fallback to tableIds (multi-table orders, comma-separated)
        if (order.getTableIds() != null && !order.getTableIds().isEmpty()) {
            return order.getTableIds();
        }
        return null;
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

            if ("NETWORK".equalsIgnoreCase(settings.getConnectionType())) {
                return testNetworkPrinter(settings);
            } else {
                javax.print.PrintService printService = findPrintService(settings.getPrinterName());
                return printService != null;
            }
        } catch (Exception e) {
            log.error("Failed to test printer", e);
            return false;
        }
    }

    /**
     * Test network printer connection by attempting to connect to the socket
     */
    private boolean testNetworkPrinter(PrinterSettings settings) {
        String ipAddress = settings.getIpAddress();
        Integer port = settings.getPort() != null ? settings.getPort() : 9100;

        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            log.error("Network printer IP address not configured");
            return false;
        }

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ipAddress, port), 3000);
            log.info("Network printer test successful: {}:{}", ipAddress, port);
            return true;
        } catch (Exception e) {
            log.error("Network printer test failed for {}:{} - {}", ipAddress, port, e.getMessage());
            return false;
        }
    }
}
