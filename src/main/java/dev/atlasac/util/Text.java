package dev.atlasac.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Map;

/** Kleine MiniMessage-Hilfen. */
public final class Text {
	private static final MiniMessage MM = MiniMessage.miniMessage();

	private Text() {
	}

	public static Component mm(String input) {
		return MM.deserialize(input);
	}

	/** Fuer Item-Namen/Lore: ohne Standard-Kursiv. */
	public static Component item(String input) {
		return MM.deserialize(input).decoration(TextDecoration.ITALIC, false);
	}

	/** Entfernt MiniMessage-Tags aus frei gewaehltem Text (z.B. Check-Infos). */
	public static String escape(String input) {
		return MM.escapeTags(input);
	}

	public static String apply(String template, Map<String, String> placeholders) {
		String out = template;
		for (Map.Entry<String, String> e : placeholders.entrySet()) {
			out = out.replace(e.getKey(), e.getValue());
		}
		return out;
	}

	public static String plain(Component component) {
		return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
	}

	public static String fmt(double value) {
		return String.format(java.util.Locale.ROOT, "%.3f", value);
	}

	public static String fmt1(double value) {
		return String.format(java.util.Locale.ROOT, "%.1f", value);
	}
}
