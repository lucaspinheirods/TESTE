package com.example.uberleitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê os textos da tela de oferta de corrida do Uber Motorista e extrai:
 * valor, nota, tempo/distância até o passageiro, tempo/distância da viagem e os dois endereços.
 * Java puro (não usa nada do Android), por isso dá para testar fora do celular.
 */
public final class OfferParser {

    public static final class Offer {
        public double price;
        public double rating = -1;            // -1 = nota não encontrada
        public boolean hasPickup, hasTrip;
        public double pickupKm, pickupMin, tripKm, tripMin;
        public String pickupAddress = "";
        public String dropAddress = "";

        public double totalKm()  { return pickupKm + tripKm; }
        public double totalMin() { return pickupMin + tripMin; }
        public double perKm()    { return totalKm()  > 0 ? price / totalKm() : 0; }
        public double perHour()  { return totalMin() > 0 ? price / (totalMin() / 60.0) : 0; }
    }

    private static final Pattern PRICE = Pattern.compile(
            "R\\$\\s*(\\d{1,3}(?:\\.\\d{3})*,\\d{2}|\\d+[.,]\\d{1,2}|\\d+)");

    // "14 min (6,2 km)", "5 minutos (1,8 km)", "1 h 5 min (30 km)", "7 min · 2,1 km"
    private static final Pattern TIME_DIST = Pattern.compile(
            "(?:(\\d+)\\s*h\\w*\\s*)?(\\d+)\\s*min\\w*[\\s(·•,\\-–]*([\\d.,]+)\\s*km",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern RATING_STAR = Pattern.compile(
            "(?:★|⭐|nota\\s*:?)\\s*([1-5][.,]\\d{1,2})|([1-5][.,]\\d{1,2})\\s*(?:★|⭐)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern RATING_ALONE = Pattern.compile("^([1-5][.,]\\d{1,2})$");

    private static final Pattern NOT_ADDRESS = Pattern.compile(
            "\\b(aceitar|selecionar|recusar|confirmar|toque|deslize|uber ?x|uber ?black|comfort|flash|pix|"
                    + "dinheiro|cart[aã]o|verificad[oa]|exclusiv[oa]|min(utos?)?)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern HAS_KM = Pattern.compile("\\d\\s*km\\b", Pattern.CASE_INSENSITIVE);

    private OfferParser() {}

    private static final class Occ {
        int line, endLine;
        double min, km;
        String kind; // "pickup", "trip" ou null
    }

    /** Devolve null se a tela não parece ser uma oferta de corrida. */
    public static Offer parse(List<String> rawLines) {
        List<String> lines = new ArrayList<>();
        for (String l : rawLines) {
            if (l == null) continue;
            String s = l.replace(' ', ' ').replace(' ', ' ').trim();
            if (!s.isEmpty()) lines.add(s);
        }
        if (lines.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        int[] starts = new int[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            starts[i] = sb.length();
            sb.append(lines.get(i)).append('\n');
        }
        String text = sb.toString();

        // 1) tempo + distância (pode vir numa linha só ou quebrado em vários textos)
        List<Occ> occs = new ArrayList<>();
        Matcher m = TIME_DIST.matcher(text);
        while (m.find()) {
            Occ o = new Occ();
            double hours = m.group(1) != null ? num(m.group(1)) : 0;
            o.min = hours * 60 + num(m.group(2));
            o.km = num(m.group(3));
            o.line = lineOf(starts, m.start());
            o.endLine = lineOf(starts, Math.max(m.start(), m.end() - 1));
            String before = text.substring(Math.max(0, m.start() - 40), m.start()).toLowerCase(Locale.ROOT);
            String after = text.substring(m.end(), Math.min(text.length(), m.end() + 25)).toLowerCase(Locale.ROOT);
            if (after.contains("distância") || after.contains("distancia") || after.contains("away")) {
                o.kind = "pickup";
            } else if (before.contains("viagem") || before.contains("trip")) {
                o.kind = "trip";
            }
            if (o.km > 0 && o.min > 0) occs.add(o);
        }
        if (occs.isEmpty()) return null;

        Occ pickup = null, trip = null;
        List<Occ> unknown = new ArrayList<>();
        for (Occ o : occs) {
            if ("pickup".equals(o.kind) && pickup == null) pickup = o;
            else if ("trip".equals(o.kind) && trip == null) trip = o;
            else unknown.add(o);
        }
        if (pickup == null && trip == null) {
            if (unknown.size() >= 2) { pickup = unknown.get(0); trip = unknown.get(1); }
            else if (unknown.size() == 1) trip = unknown.get(0);
        } else if (pickup == null && !unknown.isEmpty()) {
            pickup = unknown.get(0);
        } else if (trip == null && !unknown.isEmpty()) {
            trip = unknown.get(0);
        }

        int firstLine = Integer.MAX_VALUE;
        for (Occ o : occs) firstLine = Math.min(firstLine, o.line);

        Offer offer = new Offer();

        // 2) valor: o "R$" mais próximo, acima das distâncias (evita pegar ganhos do dia lá no topo)
        double best = 0;
        for (int i = Math.min(firstLine, lines.size() - 1); i >= Math.max(0, firstLine - 6) && best <= 0; i--) {
            String ln = lines.get(i);
            String low = ln.toLowerCase(Locale.ROOT);
            if (low.matches(".*(/|por\\s*)\\s*(km|h|hora).*")) continue;      // "R$ 2,10/km"
            if (low.matches(".*(a mais|b[oô]nus|extra|adicional|din[aâ]mic).*") || low.startsWith("+")) continue;
            Matcher pm = PRICE.matcher(ln);
            if (pm.find()) best = num(pm.group(1));
        }
        if (best <= 0) {
            for (int i = firstLine + 1; i < lines.size() && best <= 0; i++) {
                Matcher pm = PRICE.matcher(lines.get(i));
                if (pm.find()) best = num(pm.group(1));
            }
        }
        if (best <= 0) return null;
        offer.price = best;

        // 3) nota
        for (int i = Math.min(firstLine, lines.size() - 1); i >= 0 && offer.rating < 0; i--) {
            Matcher rm = RATING_STAR.matcher(lines.get(i));
            if (rm.find()) {
                offer.rating = num(rm.group(1) != null ? rm.group(1) : rm.group(2));
            }
        }
        if (offer.rating < 0) {
            for (int i = Math.min(firstLine, lines.size() - 1); i >= 0 && offer.rating < 0; i--) {
                Matcher rm = RATING_ALONE.matcher(lines.get(i));
                if (rm.find()) offer.rating = num(rm.group(1));
            }
        }

        // 4) distâncias e endereços
        if (pickup != null) {
            offer.hasPickup = true;
            offer.pickupKm = pickup.km;
            offer.pickupMin = pickup.min;
            offer.pickupAddress = findAddress(lines, pickup, occs);
        }
        if (trip != null) {
            offer.hasTrip = true;
            offer.tripKm = trip.km;
            offer.tripMin = trip.min;
            offer.dropAddress = findAddress(lines, trip, occs);
        }
        return offer;
    }

    private static String findAddress(List<String> lines, Occ occ, List<Occ> all) {
        int limit = lines.size();
        for (Occ o : all) {
            if (o != occ && o.line > occ.endLine) limit = Math.min(limit, o.line);
        }
        for (int j = occ.endLine + 1; j < limit; j++) {
            if (isAddress(lines.get(j))) return lines.get(j);
        }
        return "";
    }

    private static boolean isAddress(String s) {
        if (s.length() < 5) return false;
        String low = s.toLowerCase(Locale.ROOT);
        if (low.contains("r$") || s.contains("★") || s.contains("⭐")) return false;
        if (low.startsWith("viagem") || low.startsWith("trip")) return false;
        if (HAS_KM.matcher(s).find()) return false;
        if (NOT_ADDRESS.matcher(s).find()) return false;
        boolean hasLetter = false;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) { hasLetter = true; break; }
        }
        return hasLetter;
    }

    private static int lineOf(int[] starts, int pos) {
        int idx = 0;
        for (int i = 0; i < starts.length; i++) {
            if (starts[i] <= pos) idx = i; else break;
        }
        return idx;
    }

    static double num(String s) {
        if (s == null) return 0;
        s = s.trim().replace(" ", "").replaceAll("[.,]+$", "");
        if (s.contains(",") && s.contains(".")) s = s.replace(".", "").replace(',', '.');
        else if (s.contains(",")) s = s.replace(',', '.');
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }

    // ---------- formatação em português ----------

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    public static String dec(double v, int casas) {
        return String.format(PT_BR, "%." + casas + "f", v);
    }

    public static String money(double v) {
        return "R$ " + dec(v, 2);
    }

    public static String describe(Offer o) {
        StringBuilder b = new StringBuilder();
        b.append("Ganho por km:   ").append(money(o.perKm())).append("/km\n");
        b.append("Ganho por hora: ").append(money(o.perHour())).append("/h\n");
        b.append("Nota: ").append(o.rating >= 0 ? dec(o.rating, 2) : "não encontrada").append('\n');
        b.append("Partida: ").append(o.pickupAddress.isEmpty() ? "(não encontrado)" : o.pickupAddress).append('\n');
        b.append("Chegada: ").append(o.dropAddress.isEmpty() ? "(não encontrado)" : o.dropAddress).append('\n');
        b.append("\nValor da corrida: ").append(money(o.price));
        b.append("\nTotal: ").append(dec(o.totalKm(), 1)).append(" km em ").append(dec(o.totalMin(), 0)).append(" min");
        if (!o.hasPickup) b.append("\n(aviso: não achei a distância até o passageiro)");
        if (!o.hasTrip) b.append("\n(aviso: não achei a distância da viagem)");
        return b.toString();
    }
}
