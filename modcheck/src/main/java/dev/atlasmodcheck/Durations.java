package dev.atlasmodcheck;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Dauer-Parser ("1d12h", "30m", "2w", "1mo", "permanent") und Formatierung. */
public final class Durations {
	private static final Pattern PART = Pattern.compile("(\\d+)(mo|y|w|d|h|m|s)");
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

	private Durations() {
	}

	/** @return Millisekunden, -1 fuer permanent, 0 bei ungueltiger Eingabe */
	public static long parse(String input) {
		if (input == null) return 0;
		String s = input.toLowerCase().trim();
		if (s.equals("perm") || s.equals("permanent") || s.equals("-1")) return -1;
		Matcher m = PART.matcher(s);
		long total = 0;
		int consumed = 0;
		while (m.find()) {
			if (m.start() != consumed) return 0;
			consumed = m.end();
			long n = Long.parseLong(m.group(1));
			total += switch (m.group(2)) {
				case "y" -> n * 365L * 86_400_000L;
				case "mo" -> n * 30L * 86_400_000L;
				case "w" -> n * 7L * 86_400_000L;
				case "d" -> n * 86_400_000L;
				case "h" -> n * 3_600_000L;
				case "m" -> n * 60_000L;
				default -> n * 1000L;
			};
		}
		return consumed == s.length() ? total : 0;
	}

	public static boolean isDuration(String input) {
		return parse(input) != 0;
	}

	public static String format(long millis) {
		if (millis < 0) return "Permanent";
		long s = Math.max(1, millis / 1000);
		long d = s / 86400, h = s % 86400 / 3600, m = s % 3600 / 60, sec = s % 60;
		StringBuilder b = new StringBuilder();
		if (d > 0) b.append(d).append(d == 1 ? " Tag " : " Tage ");
		if (h > 0) b.append(h).append(" Std. ");
		if (m > 0 && d == 0) b.append(m).append(" Min. ");
		if (sec > 0 && d == 0 && h == 0) b.append(sec).append(" Sek.");
		return b.toString().trim();
	}

	public static String date(long epochMillis) {
		if (epochMillis < 0) return "Nie";
		return DATE.format(Instant.ofEpochMilli(epochMillis));
	}
}
