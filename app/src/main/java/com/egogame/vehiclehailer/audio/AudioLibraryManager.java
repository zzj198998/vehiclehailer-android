package com.egogame.vehiclehailer.audio;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 音频库管理器
 * 管理5个分类的音频列表，支持本地设备上传、重命名、删除
 * 使用SharedPreferences持久化存储
 */
public class AudioLibraryManager {

    private static final String TAG = "AudioLibraryManager";
    private static final String PREFS_NAME = "audio_library";
    private static final String KEY_PREFIX = "audio_";
    private static final String AUDIO_SUB_DIR = "audio_library";

    public enum AudioCategory {
        HAILING("车外喊话"),
        DOOR_SOUND("开关门音效"),
        LOCK_SOUND("锁车解锁"),
        CUSTOM_AUDIO("自定义音效"),
        SOUNDWAVE("模拟声浪");

        private final String displayName;

        AudioCategory(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDirName() {
            return name().toLowerCase();
        }
    }

    public static class AudioItem {
        public String id;
        public String name;
        public String filePath;
        public long durationMs;
        public long addedTime;

        public AudioItem(String id, String name, String filePath, long durationMs, long addedTime) {
            this.id = id;
            this.name = name;
            this.filePath = filePath;
            this.durationMs = durationMs;
            this.addedTime = addedTime;
        }

        public JSONObject toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", id);
                obj.put("name", name);
                obj.put("filePath", filePath);
                obj.put("durationMs", durationMs);
                obj.put("addedTime", addedTime);
                return obj;
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        public static AudioItem fromJson(JSONObject obj) {
            try {
                return new AudioItem(
                        obj.optString("id", ""),
                        obj.optString("name", ""),
                        obj.optString("filePath", ""),
                        obj.optLong("durationMs", 0),
                        obj.optLong("addedTime", 0)
                );
            } catch (Exception e) {
                return null;
            }
        }
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final File audioBaseDir;

    public AudioLibraryManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.audioBaseDir = new File(context.getFilesDir(), AUDIO_SUB_DIR);
        if (!audioBaseDir.exists()) {
            audioBaseDir.mkdirs();
        }
    }

    /**
     * 获取指定分类的音频列表
     */
    public List<AudioItem> getAudioList(AudioCategory category) {
        String json = prefs.getString(getKey(category), "[]");
        List<AudioItem> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                AudioItem item = AudioItem.fromJson(arr.getJSONObject(i));
                if (item != null) {
                    // 检查文件是否存在，不存在则过滤掉
                    if (new File(item.filePath).exists()) {
                        list.add(item);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "解析音频列表失败", e);
        }
        return list;
    }

    /**
     * 保存指定分类的音频列表
     */
    public void saveAudioList(AudioCategory category, List<AudioItem> items) {
        JSONArray arr = new JSONArray();
        for (AudioItem item : items) {
            arr.put(item.toJson());
        }
        prefs.edit().putString(getKey(category), arr.toString()).apply();
    }

    /**
     * 上传音频文件到指定分类
     */
    public AudioItem addAudio(AudioCategory category, Uri sourceUri, String displayName) {
        try {
            // 确保分类目录存在
            File categoryDir = new File(audioBaseDir, category.getDirName());
            if (!categoryDir.exists()) categoryDir.mkdirs();

            // 生成唯一文件名
            String ext = getExtension(displayName);
            if (ext.isEmpty()) ext = ".mp3";
            String fileName = System.currentTimeMillis() + "_" + sanitizeFileName(displayName);
            File destFile = new File(categoryDir, fileName);

            // 复制文件到内部存储
            try (InputStream in = context.getContentResolver().openInputStream(sourceUri);
                 FileOutputStream out = new FileOutputStream(destFile)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
            }

            // 获取显示名称（去掉扩展名）
            String name = displayName;
            int dotIndex = name.lastIndexOf('.');
            if (dotIndex > 0) {
                name = name.substring(0, dotIndex);
            }

            // 创建音频项
            AudioItem item = new AudioItem(
                    String.valueOf(System.currentTimeMillis()),
                    name,
                    destFile.getAbsolutePath(),
                    0, // 暂不获取时长
                    System.currentTimeMillis()
            );

            // 添加到列表并保存
            List<AudioItem> list = getAudioList(category);
            list.add(item);
            saveAudioList(category, list);

            Log.d(TAG, "添加音频: " + name + " -> " + destFile.getAbsolutePath());
            return item;

        } catch (Exception e) {
            Log.e(TAG, "添加音频失败", e);
            return null;
        }
    }

    /**
     * 重命名音频
     */
    public boolean renameAudio(AudioCategory category, String audioId, String newName) {
        List<AudioItem> list = getAudioList(category);
        for (AudioItem item : list) {
            if (item.id.equals(audioId)) {
                item.name = newName;
                saveAudioList(category, list);
                return true;
            }
        }
        return false;
    }

    /**
     * 删除音频（删除文件并从列表中移除）
     */
    public boolean deleteAudio(AudioCategory category, String audioId) {
        List<AudioItem> list = getAudioList(category);
        AudioItem toRemove = null;
        for (AudioItem item : list) {
            if (item.id.equals(audioId)) {
                toRemove = item;
                break;
            }
        }
        if (toRemove != null) {
            // 删除文件
            File file = new File(toRemove.filePath);
            if (file.exists()) file.delete();

            list.remove(toRemove);
            saveAudioList(category, list);
            return true;
        }
        return false;
    }

    /**
     * 获取分类的音频存储目录
     */
    public File getCategoryDir(AudioCategory category) {
        File dir = new File(audioBaseDir, category.getDirName());
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public File getAudioBaseDir() {
        return audioBaseDir;
    }

    private String getKey(AudioCategory category) {
        return KEY_PREFIX + category.name();
    }

    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex);
        }
        return "";
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_.-]", "_");
    }
}