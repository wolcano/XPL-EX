package eu.faircode.xlua.api.xlua.provider;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.robv.android.xposed.XposedBridge;
import eu.faircode.xlua.BuildConfig;
import eu.faircode.xlua.DebugUtil;
import eu.faircode.xlua.XDatabaseOld;
import eu.faircode.xlua.XLegacyCore;
import eu.faircode.xlua.XUiGroup;
import eu.faircode.xlua.api.XResult;
import eu.faircode.xlua.api.hook.LuaHookPacket;
import eu.faircode.xlua.api.xmock.database.LuaSettingsManager;
import eu.faircode.xlua.api.xstandard.UserIdentityPacket;
import eu.faircode.xlua.api.xlua.XLuaCall;

import eu.faircode.xlua.api.hook.XLuaHook;
import eu.faircode.xlua.api.xlua.call.GetVersionCommand;
import eu.faircode.xlua.api.xlua.database.LuaHookManager;
import eu.faircode.xlua.utilities.StringUtil;

public class XLuaHookProvider {
    private static final String TAG = "XLua.XHookProvider";

    public static List<String> getCollections(Context context, XDatabaseOld db, int userId) {
        //check this
        String value = LuaSettingsManager.getSettingValue(context, db, "collection",  userId, UserIdentityPacket.GLOBAL_NAMESPACE);
        List<String> result = new ArrayList<>();

        if(DebugUtil.isDebug())
            Log.i(TAG, "collection=" + value);

        if(!StringUtil.isValidString(value)) {
            value = LuaSettingsManager.DEFAULT_COLLECTIONS;
            LuaSettingsManager.putSetting(context, db, "collection", value);
        }

        if(value.contains(",")) Collections.addAll(result, value.split(","));
        else result.add(value);

        if(DebugUtil.isDebug())
            Log.i(TAG, "collection size=" + result.size());

        return result;
    }

    public static XResult putHook(Context context, XDatabaseOld database, LuaHookPacket packet) throws Throwable { return putHook(context, database, packet.getId(), packet.getDefinition());  }
    public static XResult putHook(Context context, XDatabaseOld database, String id, String definition) throws Throwable {
        XResult res = XResult.create().setMethodName("putHook").setExtra("id=" + id);
        if (!StringUtil.isValidString(id))
            return res.appendErrorMessage("ID Missing from Hook!", TAG).setFailed();

        XLuaHook hook = null;
        if(definition != null) {
            hook = new XLuaHook();
            hook.fromJSONObject(new JSONObject(definition));
        }

        if(hook != null) {
            hook.validate();
            if(!id.equals(hook.getObjectId())) {
                return res.appendErrorMessage("ID Mismatch: Given=" + id + "  Parsed=" + hook.getObjectId(), TAG).setFailed();
            }
        }

        //if(!XLegacyCore.updateHookCache(context, hook, id))
        //    return res.appendErrorMessage("Failed at Updating Hook Cache, id=" + id, TAG).setFailed();

        return LuaHookManager.updateHook(database, hook, id);
    }

    public static boolean isAvailable(Context context) {
        try {
            // First, check if XposedBridge is accessible (indicates module is loaded)
            try {
                Class.forName("de.robv.android.xposed.XposedBridge");
                // XposedBridge is available, module is likely active
                Log.i(TAG, "XposedBridge detected - module is active");
                return true;
            } catch (ClassNotFoundException ignored) {
                // XposedBridge not found, fall through to provider check
            }

            // Fallback: Try to contact the Settings provider
            PackageInfo pi = context.getPackageManager().getPackageInfo(BuildConfig.APPLICATION_ID, 0);
            Bundle result = context.getContentResolver()
                    .call(XProvider.getURI(), "xlua", "getVersion", new Bundle());

            if (result != null && pi.versionCode == result.getInt("version")) {
                Log.i(TAG, "Module version verified via provider");
                return true;
            }

            // If we got here, neither check succeeded
            Log.w(TAG, "Module activation check failed - please ensure LSPosed has activated the module for 'System Framework' and restart");
            return false;
        } catch (Throwable ex) {
            Log.e(TAG, "Error checking module availability: " + ex.getMessage());
            Log.e(TAG, Log.getStackTraceString(ex));
            try {
                XposedBridge.log(ex);
            } catch (Throwable ignored) {
                // XposedBridge might not be available
            }
            return false;
        }
    }

    public static List<XUiGroup> getUiGroups(Context context) {
        List<String> groups = XLuaCall.getGroups(context);
        List<XUiGroup> uiGroups = new ArrayList<>();

        if(groups == null)
            return uiGroups;

        Resources res = context.getResources();
        for(String name : groups) {
            String g = name.toLowerCase().replaceAll("[^a-z]", "_");
            int id = res.getIdentifier("group_" + g, "string", context.getPackageName());

            XUiGroup group = new XUiGroup();
            group.name = name;
            group.title = (id > 0 ? res.getString(id) : name);
            uiGroups.add(group);
        }

        return uiGroups;
    }
}
