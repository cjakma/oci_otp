package org.pmoci.kskillauth;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

/** Stores a small local audit trail so each device keeps both request and approval history. */
final class AuthRequestHistoryStore {
    private static final String PREFS = "pmoci_otp_request_history";
    private static final String KEY_EVENTS = "events_json";
    private static final int MAX_EVENTS = 30;

    private AuthRequestHistoryStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void recordPending(Context context, String challengeId, String adminId, String expiresAt) {
        recordPending(context, AppPrefs.accountId(context), challengeId, adminId, expiresAt);
    }

    static void recordPending(Context context, String accountId, String challengeId, String adminId, String expiresAt) {
        upsert(context, accountId, challengeId, adminId, expiresAt, "pending", nowIso(), null);
    }

    static void recordApproved(Context context, String challengeId, String adminId, String approvedAt) {
        recordApproved(context, AppPrefs.accountId(context), challengeId, adminId, approvedAt);
    }

    static void recordApproved(Context context, String accountId, String challengeId, String adminId, String approvedAt) {
        upsert(context, accountId, challengeId, adminId, null, "approved", null,
                (approvedAt == null || approvedAt.trim().isEmpty()) ? nowIso() : approvedAt);
    }

    private static void upsert(Context context, String accountId, String challengeId, String adminId, String expiresAt,
                               String status, String requestedAt, String approvedAt) {
        if (challengeId == null || challengeId.trim().isEmpty()) return;
        try {
            JSONArray events = readEvents(context);
            JSONObject existing = null;
            for (int i = 0; i < events.length(); i++) {
                JSONObject obj = events.optJSONObject(i);
                if (obj != null && challengeId.equals(obj.optString("challenge_id"))) {
                    existing = obj;
                    events.remove(i);
                    break;
                }
            }
            if (existing == null) existing = new JSONObject();
            existing.put("challenge_id", challengeId);
            String account = accountId == null || accountId.trim().isEmpty() ? AppPrefs.accountId(context) : accountId.trim();
            existing.put("account_id", account);
            if (adminId != null && !adminId.trim().isEmpty()) existing.put("admin_id", adminId);
            if (expiresAt != null && !expiresAt.trim().isEmpty()) existing.put("expires_at", expiresAt);
            if (requestedAt != null && !requestedAt.trim().isEmpty() && !existing.has("requested_at")) {
                existing.put("requested_at", requestedAt);
            }
            if (approvedAt != null && !approvedAt.trim().isEmpty()) existing.put("approved_at", approvedAt);
            existing.put("status", status);

            JSONArray next = new JSONArray();
            next.put(existing); // newest first
            for (int i = 0; i < events.length() && next.length() < MAX_EVENTS; i++) {
                JSONObject obj = events.optJSONObject(i);
                if (obj != null) next.put(obj);
            }
            prefs(context).edit().putString(KEY_EVENTS, next.toString()).apply();
        } catch (Exception ignored) {
            // History is best-effort; never block authentication because local audit storage failed.
        }
    }

    private static JSONArray readEvents(Context context) {
        try {
            return new JSONArray(prefs(context).getString(KEY_EVENTS, "[]"));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    static String describeRecent(Context context) {
        JSONArray events = readEvents(context);
        if (events.length() == 0) {
            return "최근 인증 요청 이력이 없습니다.";
        }
        StringBuilder out = new StringBuilder("최근 인증 요청 이력\n");
        int count = Math.min(5, events.length());
        for (int i = 0; i < count; i++) {
            JSONObject obj = events.optJSONObject(i);
            if (obj == null) continue;
            String cid = obj.optString("challenge_id", "");
            String shortId = cid.length() > 8 ? cid.substring(0, 8) : cid;
            String status = "approved".equals(obj.optString("status")) ? "인증됨" : "요청됨";
            String account = obj.optString("account_id", "");
            String admin = obj.optString("admin_id", "");
            String time = "approved".equals(obj.optString("status"))
                    ? obj.optString("approved_at", obj.optString("requested_at", ""))
                    : obj.optString("requested_at", "");
            out.append("• ").append(status).append(" #").append(shortId);
            if (!account.isEmpty()) out.append(" · 계정 ").append(account);
            if (!admin.isEmpty()) out.append(" · ").append(admin);
            if (!time.isEmpty()) out.append(" · ").append(time);
            if (i < count - 1) out.append('\n');
        }
        return out.toString();
    }

    private static String nowIso() {
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM, Locale.KOREA)
                .format(new Date());
    }
}
