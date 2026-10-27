package dev.atlasac.discord;

import dev.atlasac.AtlasAC;
import dev.atlasac.punish.Punishment;
import dev.atlasac.util.Durations;
import org.bukkit.configuration.ConfigurationSection;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/** Schickt eine Discord-Nachricht, sobald ein Spieler gebannt oder IP-gebannt wird. */
public final class DiscordWebhook {
	private final AtlasAC plugin;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	public DiscordWebhook(AtlasAC plugin) {
		this.plugin = plugin;
	}

	public void sendBan(Punishment p, String uuid, boolean ipBan) {
		ConfigurationSection c = plugin.getConfig().getConfigurationSection("discord");
		if (c == null || !c.getBoolean("enabled", false)) return;
		String url = c.getString("webhook-url", "");
		if (url.isBlank() || !url.startsWith("https://")) return;

		int color = 0xE53935;
		try {
			color = Integer.parseInt(c.getString("color", "#E53935").replace("#", ""), 16);
		} catch (NumberFormatException ignored) {
		}
		String duration = p.permanent() ? "Permanent" : Durations.format(p.expires() - p.created());
		StringBuilder fields = new StringBuilder();
		field(fields, "Spieler", p.name(), true);
		field(fields, "Art", ipBan ? "IP-Ban" : "Ban", true);
		field(fields, "Dauer", duration, true);
		field(fields, "Grund", p.reason(), false);
		field(fields, "Von", p.staff(), true);
		field(fields, "Endet", p.permanent() ? "Nie" : "<t:" + (p.expires() / 1000) + ":f>", true);
		field(fields, "Fall-ID", "#" + p.id(), true);

		StringBuilder json = new StringBuilder("{");
		json.append("\"username\":").append(str(c.getString("username", "Atlas AntiCheat")));
		String avatar = c.getString("avatar-url", "");
		if (!avatar.isBlank()) json.append(",\"avatar_url\":").append(str(avatar));
		json.append(",\"embeds\":[{")
				.append("\"title\":").append(str((ipBan ? "IP-Ban: " : "Ban: ") + p.name()))
				.append(",\"color\":").append(color)
				.append(",\"timestamp\":").append(str(Instant.ofEpochMilli(p.created()).toString()))
				.append(",\"fields\":[").append(fields).append("]");
		if (uuid != null) {
			json.append(",\"thumbnail\":{\"url\":").append(str("https://mc-heads.net/avatar/" + uuid + "/128")).append("}");
		}
		json.append(",\"footer\":{\"text\":\"Atlas AntiCheat\"}}]");
		json.append(",\"allowed_mentions\":{\"parse\":[]}}");

		HttpRequest req = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(10))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json.toString()))
				.build();
		http.sendAsync(req, HttpResponse.BodyHandlers.discarding()).whenComplete((res, err) -> {
			if (err != null) plugin.getLogger().warning("Discord-Webhook fehlgeschlagen: " + err.getMessage());
			else if (res.statusCode() >= 300) plugin.getLogger().warning("Discord-Webhook antwortete mit HTTP " + res.statusCode());
		});
	}

	private static void field(StringBuilder b, String name, String value, boolean inline) {
		if (!b.isEmpty()) b.append(',');
		String v = value == null || value.isBlank() ? "-" : value;
		if (v.length() > 1000) v = v.substring(0, 1000);
		b.append("{\"name\":").append(str(name)).append(",\"value\":").append(str(v)).append(",\"inline\":").append(inline).append('}');
	}

	private static String str(String s) {
		StringBuilder b = new StringBuilder("\"");
		for (char ch : s.toCharArray()) {
			switch (ch) {
				case '"' -> b.append("\\\"");
				case '\\' -> b.append("\\\\");
				case '\n' -> b.append("\\n");
				case '\r' -> b.append("\\r");
				case '\t' -> b.append("\\t");
				default -> {
					if (ch < 0x20) b.append(String.format("\\u%04x", (int) ch));
					else b.append(ch);
				}
			}
		}
		return b.append('"').toString();
	}
}
