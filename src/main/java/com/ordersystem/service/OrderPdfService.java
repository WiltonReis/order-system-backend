package com.ordersystem.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.ordersystem.dto.response.OrderDetailResponse;
import com.ordersystem.dto.response.OrderItemResponse;
import com.ordersystem.enums.OrderStatus;
import io.micrometer.core.annotation.Timed;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class OrderPdfService {

    private static final Color CLR_HEADER_BG   = new Color(0x1e, 0x29, 0x3b);
    private static final Color CLR_SECTION_BG  = new Color(0xf1, 0xf5, 0xf9);
    private static final Color CLR_ROW_ALT     = new Color(0xf8, 0xfa, 0xfc);
    private static final Color CLR_BORDER      = new Color(0xe2, 0xe8, 0xf0);
    private static final Color CLR_TEXT_DARK   = new Color(0x0f, 0x17, 0x2a);
    private static final Color CLR_TEXT_MUTED  = new Color(0x64, 0x74, 0x8b);
    private static final Color CLR_TABLE_HDR   = new Color(0x33, 0x41, 0x55);
    private static final Color CLR_WHITE       = Color.WHITE;
    private static final Color CLR_GREEN       = new Color(0x16, 0xa3, 0x4a);
    private static final Color CLR_YELLOW      = new Color(0xca, 0x8a, 0x04);
    private static final Color CLR_RED         = new Color(0xdc, 0x26, 0x26);
    private static final Color CLR_TOTAL_LINE  = new Color(0x0f, 0x17, 0x2a);

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final NumberFormat BRL_FMT = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    @Timed("pdf.generation")
    public byte[] generate(OrderDetailResponse order) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 40, 40, 40, 50);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(new FooterEvent(order));
            doc.open();

            addHeader(doc, order);
            addSpacing(doc, 12);
            addInfoSection(doc, order);
            addSpacing(doc, 16);
            addItemsSection(doc, order);
            addSpacing(doc, 12);
            addTotalsSection(doc, order);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar PDF do pedido", e);
        }
    }

    // ─── Header ──────────────────────────────────────────────────────────────

    private void addHeader(Document doc, OrderDetailResponse order) throws DocumentException {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{3f, 1f});

        // Left: title + subtitle
        PdfPCell left = new PdfPCell();
        left.setBackgroundColor(CLR_HEADER_BG);
        left.setBorder(Rectangle.NO_BORDER);
        left.setPadding(16);
        left.setPaddingBottom(14);

        Paragraph title = new Paragraph();
        title.add(new Chunk("PEDIDO  ", fb(18, CLR_WHITE)));
        title.add(new Chunk("#" + nvl(order.getOrderCode()), fb(18, new Color(0x93, 0xc5, 0xfd))));
        left.addElement(title);

        Paragraph sub = new Paragraph();
        sub.setSpacingBefore(4);
        if (order.getCustomerName() != null && !order.getCustomerName().isBlank()) {
            sub.add(new Chunk("Cliente: " + order.getCustomerName(), f(10, new Color(0xcb, 0xd5, 0xe1))));
        } else {
            sub.add(new Chunk("Sistema de Gestão de Pedidos", f(10, new Color(0xcb, 0xd5, 0xe1))));
        }
        left.addElement(sub);

        // Right: status badge
        Color statusBg = statusColor(order.getStatus());
        PdfPCell right = new PdfPCell();
        right.setBackgroundColor(CLR_HEADER_BG);
        right.setBorder(Rectangle.NO_BORDER);
        right.setPadding(16);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        right.setVerticalAlignment(Element.ALIGN_MIDDLE);

        PdfPTable badge = new PdfPTable(1);
        badge.setWidthPercentage(100);
        PdfPCell badgeCell = new PdfPCell(new Phrase(statusLabel(order.getStatus()), fb(9, CLR_WHITE)));
        badgeCell.setBackgroundColor(statusBg);
        badgeCell.setBorder(Rectangle.NO_BORDER);
        badgeCell.setPadding(6);
        badgeCell.setPaddingLeft(10);
        badgeCell.setPaddingRight(10);
        badgeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        badge.addCell(badgeCell);
        right.addElement(badge);

        header.addCell(left);
        header.addCell(right);
        doc.add(header);
    }

    // ─── Info section ─────────────────────────────────────────────────────────

    private void addInfoSection(Document doc, OrderDetailResponse order) throws DocumentException {
        addSectionTitle(doc, "INFORMAÇÕES DO PEDIDO");
        addSpacing(doc, 6);

        PdfPTable grid = new PdfPTable(4);
        grid.setWidthPercentage(100);
        grid.setWidths(new float[]{1.2f, 1.8f, 1.2f, 1.8f});

        // Row 1: Código | value | Criado em | value
        addInfoPair(grid, "Código", "#" + nvl(order.getOrderCode()));
        addInfoPair(grid, "Criado em", fmt(order.getCreatedAt()));

        // Row 2: Criado por | value | Cliente | value
        addInfoPair(grid, "Criado por", nvl(order.getUser() != null ? order.getUser().getName() : null));
        addInfoPair(grid, "Cliente", nvl(order.getCustomerName()));

        // Row 3 (conditional): completion info
        if (order.getCompletedAt() != null || order.getCompletedByName() != null) {
            addInfoPair(grid, "Finalizado em", fmt(order.getCompletedAt()));
            addInfoPair(grid, "Finalizado por", nvl(order.getCompletedByName()));
        }

        // Row 4 (conditional): cancellation info
        if (order.getCanceledAt() != null || order.getCanceledByName() != null) {
            addInfoPair(grid, "Cancelado em", fmt(order.getCanceledAt()));
            addInfoPair(grid, "Cancelado por", nvl(order.getCanceledByName()));
        }

        doc.add(grid);
    }

    private void addInfoPair(PdfPTable grid, String label, String value) {
        PdfPCell lbl = new PdfPCell(new Phrase(label, f(8, CLR_TEXT_MUTED)));
        lbl.setBackgroundColor(CLR_SECTION_BG);
        lbl.setBorderColor(CLR_BORDER);
        lbl.setBorderWidth(0.5f);
        lbl.setPadding(7);
        lbl.setPaddingBottom(4);

        PdfPCell val = new PdfPCell(new Phrase(value, fb(9, CLR_TEXT_DARK)));
        val.setBackgroundColor(CLR_WHITE);
        val.setBorderColor(CLR_BORDER);
        val.setBorderWidth(0.5f);
        val.setPadding(7);
        val.setPaddingBottom(4);

        grid.addCell(lbl);
        grid.addCell(val);
    }

    // ─── Items section ────────────────────────────────────────────────────────

    private void addItemsSection(Document doc, OrderDetailResponse order) throws DocumentException {
        addSectionTitle(doc, "ITENS DO PEDIDO");
        addSpacing(doc, 6);

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{4f, 1f, 1.6f, 1.6f});

        String[] headers = {"Produto", "Qtd", "Preço Unit.", "Subtotal"};
        int[] aligns = {Element.ALIGN_LEFT, Element.ALIGN_CENTER, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT};
        for (int i = 0; i < headers.length; i++) {
            PdfPCell hdr = new PdfPCell(new Phrase(headers[i], fb(9, CLR_WHITE)));
            hdr.setBackgroundColor(CLR_TABLE_HDR);
            hdr.setBorder(Rectangle.NO_BORDER);
            hdr.setPadding(8);
            hdr.setHorizontalAlignment(aligns[i]);
            table.addCell(hdr);
        }

        List<OrderItemResponse> items = order.getItems();
        for (int i = 0; i < items.size(); i++) {
            OrderItemResponse it = items.get(i);
            Color rowBg = (i % 2 == 0) ? CLR_WHITE : CLR_ROW_ALT;
            BigDecimal subtotal = it.getPrice().multiply(BigDecimal.valueOf(it.getQuantity()));

            addItemCell(table, it.getProductName(), rowBg, Element.ALIGN_LEFT, f(9, CLR_TEXT_DARK));
            addItemCell(table, String.valueOf(it.getQuantity()), rowBg, Element.ALIGN_CENTER, f(9, CLR_TEXT_DARK));
            addItemCell(table, brl(it.getPrice()), rowBg, Element.ALIGN_RIGHT, f(9, CLR_TEXT_DARK));
            addItemCell(table, brl(subtotal), rowBg, Element.ALIGN_RIGHT, fb(9, CLR_TEXT_DARK));
        }

        doc.add(table);
    }

    private void addItemCell(PdfPTable table, String text, Color bg, int align, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setBorderColor(CLR_BORDER);
        cell.setBorderWidth(0.5f);
        cell.setPadding(7);
        cell.setHorizontalAlignment(align);
        table.addCell(cell);
    }

    // ─── Totals section ───────────────────────────────────────────────────────

    private void addTotalsSection(Document doc, OrderDetailResponse order) throws DocumentException {
        BigDecimal subtotal = order.getItems().stream()
                .map(it -> it.getPrice().multiply(BigDecimal.valueOf(it.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal discount = order.getDiscount() != null ? order.getDiscount() : BigDecimal.ZERO;

        PdfPTable outer = new PdfPTable(2);
        outer.setWidthPercentage(100);
        outer.setWidths(new float[]{1f, 1f});

        // Left: empty
        PdfPCell empty = new PdfPCell();
        empty.setBorder(Rectangle.NO_BORDER);
        outer.addCell(empty);

        // Right: totals block
        PdfPTable totals = new PdfPTable(2);
        totals.setWidthPercentage(100);

        addTotalRow(totals, "Subtotal", brl(subtotal), false, false);
        if (discount.compareTo(BigDecimal.ZERO) > 0) {
            addTotalRow(totals, "Desconto", "- " + brl(discount), false, false);
        }
        addTotalRow(totals, "Total", brl(order.getTotal()), true, true);

        PdfPCell right = new PdfPCell(totals);
        right.setBorder(Rectangle.NO_BORDER);
        right.setPadding(0);
        outer.addCell(right);

        doc.add(outer);
    }

    private void addTotalRow(PdfPTable totals, String label, String value, boolean bold, boolean topBorder) {
        Font labelFont = bold ? fb(10, CLR_TEXT_DARK) : f(9, CLR_TEXT_MUTED);
        Font valueFont = bold ? fb(11, CLR_TOTAL_LINE) : f(9, CLR_TEXT_MUTED);

        PdfPCell lbl = new PdfPCell(new Phrase(label, labelFont));
        lbl.setBorderColor(CLR_BORDER);
        lbl.setBorderWidthTop(topBorder ? 1f : 0f);
        lbl.setBorderWidthBottom(0f);
        lbl.setBorderWidthLeft(0f);
        lbl.setBorderWidthRight(0f);
        lbl.setPadding(6);
        lbl.setBackgroundColor(topBorder ? CLR_SECTION_BG : CLR_WHITE);
        totals.addCell(lbl);

        PdfPCell val = new PdfPCell(new Phrase(value, valueFont));
        val.setBorderColor(CLR_BORDER);
        val.setBorderWidthTop(topBorder ? 1f : 0f);
        val.setBorderWidthBottom(0f);
        val.setBorderWidthLeft(0f);
        val.setBorderWidthRight(0f);
        val.setPadding(6);
        val.setHorizontalAlignment(Element.ALIGN_RIGHT);
        val.setBackgroundColor(topBorder ? CLR_SECTION_BG : CLR_WHITE);
        totals.addCell(val);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void addSectionTitle(Document doc, String title) throws DocumentException {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell(new Phrase(title, fb(8, CLR_TEXT_MUTED)));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(CLR_BORDER);
        cell.setBorderWidth(1f);
        cell.setPadding(0);
        cell.setPaddingBottom(4);
        cell.setBackgroundColor(CLR_WHITE);
        t.addCell(cell);
        doc.add(t);
    }

    private void addSpacing(Document doc, float pt) throws DocumentException {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(pt);
        doc.add(p);
    }

    private Font f(float size, Color color) {
        try {
            BaseFont bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            return new Font(bf, size, Font.NORMAL, color);
        } catch (Exception e) {
            return new Font(Font.HELVETICA, size, Font.NORMAL, color);
        }
    }

    private Font fb(float size, Color color) {
        try {
            BaseFont bf = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            return new Font(bf, size, Font.BOLD, color);
        } catch (Exception e) {
            return new Font(Font.HELVETICA, size, Font.BOLD, color);
        }
    }

    private String fmt(LocalDateTime dt) {
        return dt != null ? dt.format(DT_FMT) : "—";
    }

    private String brl(BigDecimal value) {
        return value != null ? BRL_FMT.format(value) : "R$ 0,00";
    }

    private String statusLabel(OrderStatus status) {
        return switch (status) {
            case OPEN -> "EM ABERTO";
            case COMPLETED -> "FINALIZADO";
            case CANCELED -> "CANCELADO";
        };
    }

    private Color statusColor(OrderStatus status) {
        return switch (status) {
            case OPEN -> CLR_YELLOW;
            case COMPLETED -> CLR_GREEN;
            case CANCELED -> CLR_RED;
        };
    }

    private String nvl(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }

    // ─── Footer event ─────────────────────────────────────────────────────────

    private class FooterEvent extends PdfPageEventHelper {

        private final OrderDetailResponse order;

        FooterEvent(OrderDetailResponse order) {
            this.order = order;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document doc) {
            PdfContentByte cb = writer.getDirectContent();
            Font footerFont = f(7, CLR_TEXT_MUTED);

            String left = "Gerado em " + LocalDateTime.now().format(DT_FMT);
            String right = "Página " + writer.getPageNumber();

            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                    new Phrase(left, footerFont), doc.leftMargin(), 25, 0);
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                    new Phrase(right, footerFont), doc.right(), 25, 0);
        }
    }
}
