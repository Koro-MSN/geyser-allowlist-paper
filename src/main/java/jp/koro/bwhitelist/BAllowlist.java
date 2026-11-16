package jp.koro.allowlist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * /allowlist add <gamertag> で統合版（Floodgate）プレイヤーを
 * ホワイトリストに追加するプラグイン。
 * Geyser 公式 API で XUID を引き、Floodgate UUID を計算して
 * whitelist.json に直接追記し、whitelist をリロードする。
 */
public class BAllowlist extends JavaPlugin implements CommandExecutor, TabCompleter {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public void onEnable() {
        if (getCommand("allowlist") != null) {
            getCommand("allowlist").setExecutor(this);
            getCommand("allowlist").setTabCompleter(this);
        }
        getLogger().info("BAllowlist enabled");
    }

    private File whitelistFile() {
        return new File(Bukkit.getWorldContainer(), "whitelist.json");
    }

    private JsonArray readWhitelist() throws Exception {
        File f = whitelistFile();
        if (!f.exists()) return new JsonArray();
        try (FileReader r = new FileReader(f, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonArray();
        }
    }

    private void writeWhitelist(JsonArray arr) throws Exception {
        try (FileWriter w = new FileWriter(whitelistFile(), StandardCharsets.UTF_8)) {
            gson.toJson(arr, w);
        }
    }

    /** Geyser 公式 API でゲーマータグから XUID を引く（非同期で呼ぶこと） */
    private Long lookupXuid(String gamertag) {
        try {
            String url = "https://api.geysermc.org/v2/xbox/xuid/"
                    + URLEncoder.encode(gamertag, StandardCharsets.UTF_8);
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(10000);
            con.setReadTimeout(15000);
            con.setRequestProperty("User-Agent", "BAllowlist-Plugin");
            if (con.getResponseCode() != 200) return null;
            try (InputStreamReader r = new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
                return obj.has("xuid") ? obj.get("xuid").getAsLong() : null;
            }
        } catch (Exception e) {
            getLogger().warning("XUID lookup failed: " + e.getMessage());
            return null;
        }
    }

    /** XUID から Floodgate UUID（= new UUID(0, xuid)）を作る */
    private UUID floodgateUuid(long xuid) {
        return new UUID(0L, xuid);
    }

    /** Floodgate の表示名（接頭辞 "." + スペースは "_" に置換） */
    private String floodgateName(String gamertag) {
        return "." + gamertag.replace(" ", "_");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("使い方: /allowlist <add|remove|list> [ゲーマータグ]");
            return true;
        }
        String sub = args[0].toLowerCase();

        if (sub.equals("list")) {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    JsonArray arr = readWhitelist();
                    List<String> names = new ArrayList<>();
                    for (var el : arr) names.add(el.getAsJsonObject().get("name").getAsString());
                    Bukkit.getScheduler().runTask(this, () ->
                            sender.sendMessage("ホワイトリスト (" + names.size() + "): " + String.join(", ", names)));
                } catch (Exception e) {
                    Bukkit.getScheduler().runTask(this, () ->
                            sender.sendMessage("一覧の取得に失敗: " + e.getMessage()));
                }
            });
            return true;
        }

        if (!sub.equals("add") && !sub.equals("remove")) {
            sender.sendMessage("使い方: /allowlist <add|remove|list> [ゲーマータグ]");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("ゲーマータグを指定してください: /allowlist " + sub + " <ゲーマータグ>");
            return true;
        }
        String gamertag = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            Long xuid = lookupXuid(gamertag);
            if (xuid == null) {
                Bukkit.getScheduler().runTask(this, () ->
                        sender.sendMessage("XUID が見つからなかった: " + gamertag + "（ゲーマータグを確認してね）"));
                return;
            }
            UUID uuid = floodgateUuid(xuid);
            String name = floodgateName(gamertag);
            Bukkit.getScheduler().runTask(this, () -> {
                try {
                    JsonArray arr = readWhitelist();
                    boolean found = false;
                    JsonArray kept = new JsonArray();
                    for (var el : arr) {
                        JsonObject o = el.getAsJsonObject();
                        if (o.get("uuid").getAsString().equals(uuid.toString())) {
                            found = true;
                            if (sub.equals("add")) kept.add(o);
                        } else {
                            kept.add(o);
                        }
                    }
                    if (sub.equals("add")) {
                        if (found) {
                            sender.sendMessage("登録済み: " + name);
                            return;
                        }
                        JsonObject entry = new JsonObject();
                        entry.addProperty("uuid", uuid.toString());
                        entry.addProperty("name", name);
                        kept.add(entry);
                        writeWhitelist(kept);
                        Bukkit.getServer().reloadWhitelist();
                        sender.sendMessage("追加した: " + name + "（現在 " + kept.size() + " 人）");
                    } else {
                        if (!found) {
                            sender.sendMessage("ホワイトリストにいない: " + name);
                            return;
                        }
                        writeWhitelist(kept);
                        Bukkit.getServer().reloadWhitelist();
                        sender.sendMessage("削除した: " + name + "（現在 " + kept.size() + " 人）");
                    }
                } catch (Exception e) {
                    sender.sendMessage("失敗: " + e.getMessage());
                    getLogger().warning("whitelist update failed: " + e);
                }
            });
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String s : List.of("add", "remove", "list")) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
            return out;
        }
        return List.of();
    }
}
