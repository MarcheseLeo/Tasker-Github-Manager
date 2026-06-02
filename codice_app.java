import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.util.function.Consumer;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.webkit.WebView;
import android.graphics.Color;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Button;
import android.view.ViewGroup;
import android.view.View;
import io.reactivex.subjects.SingleSubject;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.Collections;

/* ⚠️ VARIABILI TASKER ⚠️ */
String githubToken = "%token";
String baseGasUrl = "%GAS_URL";
String updateJsonUrl = "%update_url"; 
String appVersion = "1.3.1"; /* Versione attuale scritta nel codice */

uiReadySignal = SingleSubject.create();
dialogClosedSignal = SingleSubject.create();

loadingHtml = "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'>" +
    "<style>body { font-family: 'Segoe UI', sans-serif; display: flex; justify-content: center; align-items: center; height: 80vh; background: transparent; color: #555; }" +
    "@media (prefers-color-scheme: dark) { body { color: #ccc; } } " +
    ".loader { border: 4px solid #f3f3f3; border-top: 4px solid #007bff; border-radius: 50%; width: 40px; height: 40px; animation: spin 1s linear infinite; margin: 0 auto 16px auto; }" +
    "@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }" +
    ".container { text-align: center; font-weight: bold; font-size: 18px; }" +
    "</style></head><body><div class='container'><div class='loader'></div><div>Sincronizzazione...</div></div></body></html>";

consumer = new Consumer() {
    accept(Object activityObj) {
        Activity currentActivity = (Activity) activityObj;
        
        LinearLayout layout = new LinearLayout(currentActivity);
        layout.setOrientation(1); 
        
        WebView loaderWebView = new WebView(currentActivity);
        loaderWebView.setBackgroundColor(Color.TRANSPARENT);
        loaderWebView.getSettings().setJavaScriptEnabled(true);
        loaderWebView.setFocusable(true);
        loaderWebView.setFocusableInTouchMode(true);
        
        LinearLayout.LayoutParams wvParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        layout.addView(loaderWebView, wvParams);
        
        Button btnClose = new Button(currentActivity);
        btnClose.setText("✕ Chiudi Dashboard");
        btnClose.setTextColor(Color.WHITE);
        btnClose.setBackgroundColor(Color.parseColor("#334155"));
        
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layout.addView(btnClose, btnParams);

        final Dialog dialog = new Dialog(currentActivity, android.R.style.Theme_DeviceDefault_NoActionBar);
        dialog.setContentView(layout);
        
        DialogInterface.OnCancelListener cancelListener = new DialogInterface.OnCancelListener() {
            onCancel(DialogInterface d) { dialogClosedSignal.onSuccess("cancelled"); currentActivity.finish(); }
        };
        dialog.setOnCancelListener(cancelListener);
        
        btnClose.setOnClickListener(new View.OnClickListener() {
            onClick(View v) { dialog.dismiss(); dialogClosedSignal.onSuccess("closed"); currentActivity.finish(); }
        });

        loaderWebView.loadDataWithBaseURL(null, loadingHtml, "text/html", "UTF-8", null);
        dialog.show();

        dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        loaderWebView.requestFocus();

        uiReadySignal.onSuccess(new Object[]{currentActivity, loaderWebView});
    }
};

tasker.doWithActivity(consumer);
uiObjects = (Object[]) uiReadySignal.blockingGet();
activity = (Activity) uiObjects[0];
webView = (WebView) uiObjects[1];

client = new OkHttpClient.Builder().connectTimeout(60, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build();

String requestUrl = baseGasUrl + "?token=" + githubToken;
request = new Request.Builder().url(requestUrl).build();

rawData = "";
try {
    response = client.newCall(request).execute();
    rawData = response.body().string();
} catch (Exception e) {
    rawData = "Errore durante il download: " + e.getMessage();
}

lines = rawData.split("\n");

categoriesMap = new HashMap();
categoryOrder = new ArrayList();
categoryOrder.add("Epicode");
categoryOrder.add("Frontend Mentor");
categoryOrder.add("Build Week");
categoryOrder.add("Altre Repo");

for (int i = 0; i < categoryOrder.size(); i++) {
    categoriesMap.put(categoryOrder.get(i), new ArrayList());
}

currentCategory = "Altre Repo";
currentRepoTitle = "";
currentRepoLines = new ArrayList();
currentEpicodeMonth = "Altro";

pubCount = "0"; privCount = "0"; totCount = "0";

for (int i = 0; i < lines.length; i++) {
    line = lines[i].trim();
    if (line.length() == 0) { continue; }

    if (line.startsWith("📊")) { continue; } 
    else if (line.startsWith("Stats:")) {
        String[] sVals = line.substring(6).trim().split(",");
        if(sVals.length == 3) { pubCount = sVals[0]; privCount = sVals[1]; totCount = sVals[2]; }
        continue;
    } else if (line.startsWith("🔹")) {
        if (currentRepoTitle.length() > 0) {
            List catList = (List) categoriesMap.get(currentCategory);
            catList.add(new Object[]{currentRepoTitle, currentRepoLines, currentEpicodeMonth});
        }
        currentRepoTitle = line;
        currentRepoLines = new ArrayList();
        currentEpicodeMonth = "Altro";
        upperLine = line.toUpperCase();
        
        if (upperLine.contains("FRONTEND-MENTOR") || upperLine.contains("FRONTEND MENTOR")) {
            currentCategory = "Frontend Mentor";
        } else if (upperLine.contains("BUILDWEEK") || upperLine.contains("BUILD WEEK")) {
            currentCategory = "Build Week";
        } else if (upperLine.contains("EPICODE")) {
            currentCategory = "Epicode";
            int mIdx = upperLine.indexOf("-M");
            if (mIdx >= 0 && mIdx + 2 < upperLine.length()) {
                String possibleMonth = upperLine.substring(mIdx + 1, mIdx + 3);
                if (possibleMonth.startsWith("M")) currentEpicodeMonth = possibleMonth; 
            }
        } else { currentCategory = "Altre Repo"; }
    } else if (line.startsWith("-----------------")) {
    } else {
        if (currentRepoTitle.length() > 0) currentRepoLines.add(line);
    }
}
if (currentRepoTitle.length() > 0) {
    List catList = (List) categoriesMap.get(currentCategory);
    catList.add(new Object[]{currentRepoTitle, currentRepoLines, currentEpicodeMonth});
}

html = new StringBuilder();
html.append("<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width, initial-scale=1'>");
html.append("<style>");
html.append("body { font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; margin: 0; padding: 16px; line-height: 1.5; background-color: transparent; color: #333; overflow-x: hidden; }");
html.append(".title { font-size: 22px; font-weight: 800; margin-bottom: 16px; text-align: center; color: #111; }");

html.append(".dashboard-stats { display: flex; justify-content: space-around; background: #fff; padding: 12px; border-radius: 12px; border: 1px solid #e2e8f0; box-shadow: 0 2px 4px rgba(0,0,0,0.05); margin-bottom: 16px; }");
html.append(".stat-box { text-align: center; }");
html.append(".stat-title { font-size: 11px; color: #64748b; font-weight: 700; text-transform: uppercase; letter-spacing: 0.5px; }");
html.append(".stat-num { font-size: 20px; font-weight: 800; color: #0f172a; }");
html.append(".search-container { margin-bottom: 12px; }");
html.append(".search-input { width: 100%; padding: 12px 16px; border-radius: 12px; border: 1px solid #cbd5e1; font-size: 15px; box-sizing: border-box; outline: none; transition: border-color 0.2s; box-shadow: 0 2px 4px rgba(0,0,0,0.02); background: #fff; color: #333; }");
html.append(".search-input:focus { border-color: #007bff; }");

html.append(".sort-select { background: #f1f5f9; border: 1px solid #cbd5e1; border-radius: 8px; padding: 6px 10px; font-weight: 600; color: #334155; font-size: 12px; outline: none; margin-bottom: 0; }");

html.append(".nav-pills-container { display: flex; gap: 16px; overflow-x: auto; padding: 4px 0 16px 0; scrollbar-width: none; margin-bottom: 4px; }");
html.append(".nav-pills-container::-webkit-scrollbar { display: none; }");
html.append(".nav-pill { display: flex; flex-direction: column; align-items: center; gap: 8px; cursor: pointer; min-width: 72px; position: relative; }");
html.append(".pill-icon { width: 52px; height: 52px; border-radius: 50%; display: flex; justify-content: center; align-items: center; font-size: 22px; transition: transform 0.2s; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }");
html.append(".nav-pill:active .pill-icon { transform: scale(0.9); }");
html.append(".pill-text { font-size: 12px; font-weight: 600; color: #475569; text-align: center; line-height: 1.1; }");
html.append(".update-badge { position: absolute; top: 0; right: 8px; width: 14px; height: 14px; background: #ef4444; border-radius: 50%; border: 2px solid #fff; display: none; }");

html.append(".p-red { background: #fee2e2; color: #dc2626; }");
html.append(".p-blue { background: #e0f2fe; color: #0284c7; }");
html.append(".p-purple { background: #f3e8ff; color: #9333ea; }");
html.append(".p-green { background: #dcfce7; color: #16a34a; }");
html.append(".p-gray { background: #f1f5f9; color: #475569; }");

html.append(".form-group { margin-bottom: 16px; }");
html.append(".form-label { display: block; font-size: 13px; font-weight: 700; color: #475569; margin-bottom: 6px; }");
html.append(".form-control { width: 100%; padding: 12px 14px; border-radius: 10px; border: 1px solid #cbd5e1; font-size: 15px; box-sizing: border-box; background: #f8fafc; color: #333; outline: none; transition: border 0.2s; }");
html.append(".form-control:focus { border-color: #007bff; background: #fff; }");
html.append(".checkbox-group { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; background: #f8fafc; padding: 12px; border-radius: 10px; border: 1px solid #e2e8f0; }");
html.append(".checkbox-group input { width: 18px; height: 18px; accent-color: #007bff; }");
html.append(".checkbox-label { font-size: 14px; font-weight: 600; color: #334155; }");
html.append(".btn-submit { width: 100%; padding: 14px; background: #007bff; color: #fff; border: none; border-radius: 10px; font-size: 16px; font-weight: 700; margin-top: 8px; cursor: pointer; transition: background 0.2s; }");
html.append(".btn-submit:active { background: #0056b3; }");
html.append(".btn-submit:disabled { background: #94a3b8; cursor: not-allowed; }");
html.append(".btn-update { background: #10b981; } .btn-update:active { background: #059669; }");

html.append("#toast { visibility: hidden; min-width: 250px; background-color: #1e293b; color: #fff; text-align: center; border-radius: 24px; padding: 14px 20px; position: fixed; z-index: 1000; left: 50%; bottom: 30px; font-size: 14px; font-weight: 600; box-shadow: 0 10px 15px -3px rgba(0,0,0,0.3); opacity: 0; transform: translate(-50%, 20px); transition: opacity 0.3s, transform 0.3s; }");
html.append("#toast.show { visibility: visible; opacity: 1; transform: translate(-50%, 0); }");

html.append(".view { display: none; animation: slideIn 0.3s ease-out; }");
html.append(".view.active { display: block; }");
html.append("@keyframes slideIn { from { opacity: 0; transform: translateX(20px); } to { opacity: 1; transform: translateX(0); } }");
html.append(".back-btn { display: none; align-items: center; gap: 8px; font-weight: 700; color: #334155; padding: 10px 16px; background: #e2e8f0; border-radius: 20px; width: fit-content; margin-bottom: 16px; cursor: pointer; font-size: 14px; transition: background 0.2s; }");
html.append(".back-btn:active { background: #cbd5e1; }");
html.append(".view-title { font-size: 18px; font-weight: 800; margin-bottom: 0; display: flex; align-items: center; gap: 8px; color: #1e293b; }");

html.append(".folder-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; margin-bottom: 20px; }");
html.append(".folder-box { aspect-ratio: 1; border-radius: 24px; padding: 16px; text-align: center; cursor: pointer; display: flex; flex-direction: column; align-items: center; justify-content: center; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05); transition: transform 0.2s; border: 2px solid transparent; }");
html.append(".folder-box:active { transform: scale(0.96); }");
html.append(".folder-icon { font-size: 38px; margin-bottom: 12px; }");
html.append(".folder-name { font-weight: 700; font-size: 15px; line-height: 1.2; }");
html.append(".folder-count { font-size: 12px; font-weight: 600; opacity: 0.7; margin-top: 6px; }");

html.append(".box-epicode { background: #faf5ff; border-color: #e9d5ff; color: #6b21a8; }");
html.append(".box-frontend { background: #f0f9ff; border-color: #bae6fd; color: #0369a1; }");
html.append(".box-build { background: #fff7ed; border-color: #fed7aa; color: #c2410c; }");
html.append(".box-altro { background: #f8fafc; border-color: #e2e8f0; color: #475569; }");

html.append(".cards-container { display: flex; flex-direction: column; gap: 16px; padding-bottom: 20px; }");
html.append(".card { width: 100%; border-radius: 16px; padding: 16px; background: #fff; border: 1px solid #e9ecef; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05); border-left: 5px solid #ccc; display: flex; flex-direction: column; box-sizing: border-box; overflow-wrap: break-word; }");

html.append(".repo-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }");
html.append(".repo { font-size: 15px; font-weight: 700; margin-bottom: 0; width: 55%; word-break: break-word; }");
html.append(".badges { display: flex; flex-direction: column; gap: 4px; align-items: flex-end; width: 42%; }");
html.append(".badge { font-size: 10px; font-weight: 700; padding: 4px 6px; border-radius: 8px; white-space: nowrap; }");
html.append(".badge-lang { background: #e2e8f0; color: #334155; }");
html.append(".badge-health { background: #f8fafc; border: 1px solid #e2e8f0; color: #475569; }");
html.append(".badge-success { background: #dcfce7; color: #166
