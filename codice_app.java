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
String appVersion = "1.5.1"; /* Versione attuale dell'app */

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

        /* FIX CORS: Usiamo un base URL fittizio per sbloccare le fetch API su Android */
        loaderWebView.loadDataWithBaseURL("https://app.githubmanager/", loadingHtml, "text/html", "UTF-8", null);
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
html.append(".badge-success { background: #dcfce7; color: #166534; }");
html.append(".badge-danger { background: #fee2e2; color: #b91c1c; }");
html.append(".badge-warning { background: #fef08a; color: #854d0e; }");

html.append(".line { font-size: 13px; margin-bottom: 6px; color: #555; display: flex; align-items: flex-start; }");
html.append(".line > div { flex: 1; min-width: 0; }");
html.append(".icon { margin-right: 8px; font-size: 14px; min-width: 16px; text-align: center; }");
html.append(".bold { font-weight: 600; margin-right: 4px; color: #222; }");
html.append("a { color: #0066cc; text-decoration: none; font-weight: 600; word-break: break-word; }");
html.append(".metrics-bar { font-size: 13px; font-weight: 600; color: #64748b; background: #f8fafc; padding: 8px; border-radius: 8px; margin: 10px 0; text-align: center; border: 1px solid #e2e8f0;}");
html.append(".actions { margin-top: auto; padding-top: 12px; border-top: 1px dashed #ddd; display: flex; gap: 6px; justify-content: flex-start; flex-wrap: wrap; }");
html.append(".btn { background: #e2e8f0; color: #334155; padding: 6px 10px; border-radius: 6px; font-size: 12px; font-weight: 600; text-decoration: none; border: none; cursor: pointer; }");
html.append(".btn-primary { background: #0f172a; color: #fff; }");
html.append(".btn-danger { background: #fee2e2; color: #b91c1c; border: 1px solid #fca5a5; }");
html.append(".btn-warning { background: #fef08a; color: #854d0e; border: 1px solid #fde047; }");
html.append(".btn-pages { background: #dcfce7; color: #166534; border: 1px solid #86efac;}");

html.append(".profile-img { width: 100px; height: 100px; border-radius: 50%; margin: 10px auto; border: 4px solid #fff; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }");
html.append(".notif-item { padding: 12px; border-bottom: 1px solid #e2e8f0; }");
html.append(".notif-item:last-child { border-bottom: none; }");

html.append("@media (prefers-color-scheme: dark) {");
html.append("  body { color: #e0e0e0; } .title { color: #ffffff; }");
html.append("  .back-btn { background: #334155; color: #f1f5f9; } .back-btn:active { background: #475569; } .view-title { color: #f1f5f9; }");
html.append("  .sort-select { background: #334155; color: #e2e8f0; border-color: #475569; }");
html.append("  .form-label { color: #94a3b8; } .form-control { background: #1e293b; border-color: #334155; color: #f8fafc; } .form-control:focus { background: #0f172a; border-color: #3b82f6; }");
html.append("  .checkbox-group { background: #1e293b; border-color: #334155; } .checkbox-label { color: #e2e8f0; }");
html.append("  .pill-text { color: #94a3b8; } .p-red { background: #450a0a; color: #fca5a5; } .p-blue { background: #082f49; color: #7dd3fc; } .p-purple { background: #3b0764; color: #d8b4fe; } .p-green { background: #064e3b; color: #86efac; } .p-gray { background: #1e293b; color: #cbd5e1; }");
html.append("  .box-epicode { background: rgba(126, 34, 206, 0.15); border-color: rgba(126, 34, 206, 0.4); color: #d8b4fe; }");
html.append("  .box-frontend { background: rgba(3, 105, 161, 0.15); border-color: rgba(3, 105, 161, 0.4); color: #7dd3fc; }");
html.append("  .box-build { background: rgba(194, 65, 12, 0.15); border-color: rgba(194, 65, 12, 0.4); color: #fdba74; }");
html.append("  .box-altro { background: rgba(71, 85, 105, 0.15); border-color: rgba(71, 85, 105, 0.4); color: #cbd5e1; }");
html.append("  .card { background: #1e1e1e; border-color: #333; } .line { color: #aaa; } .bold { color: #eee; } a { color: #66b3ff; }");
html.append("  .dashboard-stats { background: #1e1e1e; border-color: #333; } .stat-title { color: #94a3b8; } .stat-num { color: #f1f5f9; }");
html.append("  .search-input { background: #1e1e1e; border-color: #444; color: #eee; }");
html.append("  .badge-lang { background: #334155; color: #e2e8f0; } .badge-health { background: #1e293b; border-color: #334155; color: #cbd5e1; }");
html.append("  .badge-success { background: #064e3b; color: #86efac; } .badge-danger { background: #450a0a; color: #fca5a5; } .badge-warning { background: #422006; color: #fde047; }");
html.append("  .metrics-bar { background: #1e293b; color: #94a3b8; border-color: #334155;}");
html.append("  .actions { border-top-color: #444; } .btn { background: #475569; color: #f1f5f9; } .btn-primary { background: #e2e8f0; color: #0f172a; }");
html.append("  .btn-danger { background: #450a0a; color: #fca5a5; border-color: #7f1d1d; } .btn-pages { background: #064e3b; color: #86efac; border-color: #047857;}");
html.append("  .btn-warning { background: #422006; color: #fde047; border-color: #713f12; } .btn-update { background: #047857; color: #fff; border:none; } .notif-item { border-bottom-color: #333; }");
html.append("  .update-badge { border-color: #121212; }");
html.append("}");
html.append("</style></head><body>");

if (rawData.startsWith("Errore")) {
    html.append("<div class='title'>⚠️ Errore</div><div class='card'>").append(rawData).append("</div>");
} else {
    html.append("<div class='title'>📊 GITHUB MANAGER</div>");
    
    html.append("<div class='dashboard-stats'>");
    html.append("<div class='stat-box'><div class='stat-title'>Pubbliche</div><div class='stat-num'>").append(pubCount).append("</div></div>");
    html.append("<div class='stat-box'><div class='stat-title'>Private</div><div class='stat-num'>").append(privCount).append("</div></div>");
    html.append("<div class='stat-box'><div class='stat-title'>Totali</div><div class='stat-num'>").append(totCount).append("</div></div>");
    html.append("</div>");

    html.append("<div class='search-container'>");
    html.append("<input type='text' id='searchInput' class='search-input' placeholder='🔍 Cerca una repository...' onkeyup='filterRepos()'>");
    html.append("</div>");

    html.append("<div id='navPills' class='nav-pills-container'>");
    html.append("  <div class='nav-pill' onclick=\"openView('view-create-repo')\">");
    html.append("    <div class='pill-icon p-red'>➕</div><div class='pill-text'>Nuova Repo</div>");
    html.append("  </div>");
    html.append("  <div class='nav-pill' onclick=\"loadNavView('notifications')\">");
    html.append("    <div class='pill-icon p-purple'>🔔</div><div class='pill-text'>Notifiche</div>");
    html.append("  </div>");
    html.append("  <div class='nav-pill' onclick=\"loadNavView('profile')\">");
    html.append("    <div class='pill-icon p-blue'>👤</div><div class='pill-text'>Profilo</div>");
    html.append("  </div>");
    html.append("  <div class='nav-pill' onclick=\"loadNavView('explore')\">");
    html.append("    <div class='pill-icon p-green'>⭐</div><div class='pill-text'>Esplora</div>");
    html.append("  </div>");
    html.append("  <div class='nav-pill' onclick=\"loadAppInfo()\">");
    html.append("    <div class='pill-icon p-gray'>⚙️<div id='updateBadge' class='update-badge'></div></div><div class='pill-text'>App Info</div>");
    html.append("  </div>");
    html.append("</div>");

    html.append("<div id='backBtn' class='back-btn' onclick='goBack()'>⬅️ Indietro</div>");

    html.append("<div id='view-notifications' class='view'><div class='view-title' style='margin-bottom:16px;'>🔔 Notifiche GitHub</div><div id='content-notifications'></div></div>");
    html.append("<div id='view-profile' class='view'><div class='view-title' style='margin-bottom:16px;'>👤 Il tuo Profilo</div><div id='content-profile'></div></div>");
    html.append("<div id='view-explore' class='view'><div class='view-title' style='margin-bottom:16px;'>⭐ Repo di Tendenza</div><div id='content-explore'></div></div>");
    html.append("<div id='view-app-info' class='view'><div class='view-title' style='margin-bottom:16px;'>⚙️ Info & Aggiornamenti</div><div id='content-app-info'></div></div>");

    html.append("<div id='view-create-repo' class='view'>");
    html.append("<div class='view-title' style='margin-bottom:16px;'>➕ Crea Repository</div>");
    html.append("<div class='card'>");
    html.append("<div class='form-group'><label class='form-label'>Nome Repository *</label><input type='text' id='newRepoName' class='form-control' placeholder='Es. frontend-mentor-calc'></div>");
    html.append("<div class='form-group'><label class='form-label'>Descrizione</label><input type='text' id='newRepoDesc' class='form-control' placeholder='Es. Progetto HTML'></div>");
    html.append("<div class='checkbox-group'><input type='checkbox' id='newRepoPrivate'><label class='checkbox-label'>Privata 🔒</label></div>");
    html.append("<div class='checkbox-group'><input type='checkbox' id='newRepoReadme' checked><label class='checkbox-label'>README.md 📄</label></div>");
    html.append("<button id='btnCreate' class='btn-submit' onclick='createRepo()'>Crea Repository</button>");
    html.append("</div></div>");

    html.append("<div id='view-rename-repo' class='view'>");
    html.append("<div class='view-title' style='margin-bottom:16px;'>✏️ Rinomina Repository</div>");
    html.append("<div class='card'>");
    html.append("<div class='form-group'><label class='form-label'>Nuovo Nome</label><input type='text' id='renameRepoInput' class='form-control'></div>");
    html.append("<button id='btnRename' class='btn-submit' onclick='executeRename()'>Rinomina</button>");
    html.append("</div></div>");

    html.append("<div id='view-home' class='view active'><div class='folder-grid'>");
    for (int i = 0; i < categoryOrder.size(); i++) {
        String catName = (String) categoryOrder.get(i);
        List catList = (List) categoriesMap.get(catName);
        if (catList.size() > 0) {
            String cssClass = "box-altro"; String icon = "📁";
            if (catName.equals("Epicode")) { cssClass = "box-epicode"; icon = "🟣"; }
            if (catName.equals("Frontend Mentor")) { cssClass = "box-frontend"; icon = "🔵"; }
            if (catName.equals("Build Week")) { cssClass = "box-build"; icon = "🟠"; }
            
            String viewId = "view-" + catName.replace(" ", "");
            html.append("<div class='folder-box ").append(cssClass).append("' onclick=\"openView('").append(viewId).append("')\">");
            html.append("<div class='folder-icon'>").append(icon).append("</div>");
            html.append("<div class='folder-name'>").append(catName).append("</div>");
            html.append("<div class='folder-count'>").append(catList.size()).append(" Repo</div>");
            html.append("</div>");
        }
    }
    html.append("</div></div>"); 

    html.append("<div id='view-search' class='view'>");
    html.append("<div class='view-title' style='margin-bottom:16px;'>🔍 Risultati Ricerca</div>");
    html.append("<div id='search-results' class='cards-container'></div>");
    html.append("</div>");

    for (int i = 0; i < categoryOrder.size(); i++) {
        String catName = (String) categoryOrder.get(i);
        List catList = (List) categoriesMap.get(catName);

        if (catList.size() > 0) {
            String viewId = "view-" + catName.replace(" ", "");
            html.append("<div id='").append(viewId).append("' class='view'>");
            
            String icon = "📁";
            if (catName.equals("Epicode")) icon = "🟣"; 
            if (catName.equals("Frontend Mentor")) icon = "🔵"; 
            if (catName.equals("Build Week")) icon = "🟠";

            if (catName.equals("Epicode")) {
                html.append("<div class='view-title' style='margin-bottom:16px;'>").append(icon).append(" ").append(catName).append("</div>");
                List mesi = new ArrayList();
                for(int j = 0; j < catList.size(); j++) {
                    Object[] rData = (Object[]) catList.get(j);
                    String mese = (String) rData[2];
                    if (!mesi.contains(mese)) { mesi.add(mese); }
                }
                Collections.sort(mesi);

                html.append("<div class='folder-grid'>");
                for(int m = 0; m < mesi.size(); m++) {
                    String meseCorrente = (String) mesi.get(m);
                    int repoCount = 0;
                    for(int j = 0; j < catList.size(); j++) { if (((String)((Object[])catList.get(j))[2]).equals(meseCorrente)) repoCount++; }
                    
                    String subViewId = "view-epicode-" + meseCorrente;
                    html.append("<div class='folder-box box-epicode' onclick=\"openView('").append(subViewId).append("')\">");
                    html.append("<div class='folder-icon'>🗓️</div>");
                    html.append("<div class='folder-name'>Modulo ").append(meseCorrente).append("</div>");
                    html.append("<div class='folder-count'>").append(repoCount).append(" Repo</div>");
                    html.append("</div>");
                }
                html.append("</div></div>");

                for(int m = 0; m < mesi.size(); m++) {
                    String meseCorrente = (String) mesi.get(m);
                    String subViewId = "view-epicode-" + meseCorrente;
                    String cardsContainerId = "cards-" + subViewId;
                    
                    html.append("<div id='").append(subViewId).append("' class='view'>");
                    html.append("<div style='display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;'>");
                    html.append("<div class='view-title' style='margin-bottom:0;'>🗓️ Modulo ").append(meseCorrente).append("</div>");
                    html.append("<select class='sort-select' onchange=\"sortCards(this, '").append(cardsContainerId).append("')\">");
                    html.append("<option value='updated' selected>Recenti</option><option value='commit'>Ultimo Commit</option><option value='name'>A-Z</option>");
                    html.append("</select></div>");
                    
                    html.append("<div id='").append(cardsContainerId).append("' class='cards-container'>");

                    for(int j = 0; j < catList.size(); j++) {
                        Object[] rData = (Object[]) catList.get(j);
                        if (((String)rData[2]).equals(meseCorrente)) {
                            String rTitle = (String) rData[0];
                            List rLines = (List) rData[1];
                            String rRepoName=""; String rOwner=""; String rUpdatedTs="0"; String rCommitTs="0";
                            String urlRepo=""; String urlPages=""; String rLang=""; String rMetrics=""; String rIssues="0"; String rHealth=""; String rAuthor=""; String rCommit=""; String rMod=""; String rDeploy="";
                            for (int k = 0; k < rLines.size(); k++) {
                                String repoLine = (String) rLines.get(k);
                                int colonIdx = repoLine.indexOf(":");
                                if (colonIdx > 0 && repoLine.length() > colonIdx + 1) {
                                    String prefix = repoLine.substring(0, colonIdx).trim();
                                    String rest = repoLine.substring(colonIdx + 1).trim();
                                    if (prefix.equals("Repo")) urlRepo = rest; else if (prefix.equals("Pages")) urlPages = rest; else if (prefix.equals("Linguaggio")) rLang = rest; else if (prefix.equals("Metriche")) rMetrics = rest; else if (prefix.equals("Issues")) rIssues = rest; else if (prefix.equals("Stato")) rHealth = rest; else if (prefix.equals("Deploy")) rDeploy = rest; else if (prefix.equals("Autore")) rAuthor = rest; else if (prefix.equals("Commit")) rCommit = rest; else if (prefix.equals("Modifica")) rMod = rest;
                                    else if (prefix.equals("RepoName")) rRepoName = rest; else if (prefix.equals("Owner")) rOwner = rest; else if (prefix.equals("UpdatedTs")) rUpdatedTs = rest; else if (prefix.equals("CommitTs")) rCommitTs = rest;
                                }
                            }
                            html.append("<div class='card original-card' data-name='").append(rRepoName).append("' data-updated='").append(rUpdatedTs).append("' data-commit='").append(rCommitTs).append("'>");
                            html.append("<div class='repo-header'><div class='repo'>").append(rTitle).append("</div>");
                            html.append("<div class='badges'>");
                            if (rLang.length() > 0 && !rLang.equals("N/D")) html.append("<span class='badge badge-lang'>").append(rLang).append("</span>");
                            if (rHealth.length() > 0) html.append("<span class='badge badge-health'>").append(rHealth).append("</span>");
                            if (rDeploy.length() > 0) { String bClass = "badge-health"; if (rDeploy.contains("Successo")) bClass = "badge-success"; else if (rDeploy.contains("Fallito")) bClass = "badge-danger"; else if (rDeploy.contains("esecuzione")) bClass = "badge-warning"; html.append("<span class='badge ").append(bClass).append("'>").append(rDeploy).append("</span>"); }
                            html.append("</div></div>");
                            if(urlRepo.length() > 0) html.append("<div class='line'><span class='icon'>🔗</span> <div><span class='bold'>Repo:</span> <a href='").append(urlRepo).append("'>Apri GitHub</a></div></div>");
                            if(urlPages.length() > 0) html.append("<div class='line'><span class='icon'>🌐</span> <div><span class='bold'>Pages:</span> <a href='").append(urlPages).append("'>Visita Sito</a></div></div>");
                            if(rAuthor.length() > 0) html.append("<div class='line'><span class='icon'>👤</span> <div><span class='bold'>Autore:</span> ").append(rAuthor).append("</div></div>");
                            if(rCommit.length() > 0) html.append("<div class='line'><span class='icon'>📝</span> <div><span class='bold'>Commit:</span> ").append(rCommit).append("</div></div>");
                            if(rMod.length() > 0) html.append("<div class='line'><span class='icon'>📅</span> <div><span class='bold'>Modifica:</span> ").append(rMod).append("</div></div>");
                            if(rMetrics.length() > 0) html.append("<div class='metrics-bar'>").append(rMetrics).append("</div>");
                            if(urlRepo.length() > 0) {
                                html.append("<div class='actions'>");
                                int issuesNum = 0; try { issuesNum = Integer.parseInt(rIssues); } catch(Exception e){}
                                if(issuesNum > 0) html.append("<a class='btn btn-danger' href='").append(urlRepo).append("/issues'>🐛 Issues (").append(issuesNum).append(")</a>"); else html.append("<a class='btn' href='").append(urlRepo).append("/issues'>🐛 Issues</a>");
                                html.append("<a class='btn' href='").append(urlRepo).append("/pulls'>🔀 PRs</a>");
                                html.append("<button class='btn btn-warning' onclick=\"prepRenameRepo('").append(rOwner).append("', '").append(rRepoName).append("')\">✏️ Rinomina</button>");
                                html.append("<button class='btn btn-primary' onclick=\"copyText('").append(urlRepo).append(".git')\">📋 Clone</button>");
                                html.append("</div>");
                            }
                            html.append("</div>");
                        }
                    }
                    html.append("</div></div>"); 
                }
            } else {
                String cardsContainerId = "cards-cat-" + catName.replace(" ", "");
                html.append("<div style='display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;'>");
                html.append("<div class='view-title' style='margin-bottom:0;'>").append(icon).append(" ").append(catName).append("</div>");
                html.append("<select class='sort-select' onchange=\"sortCards(this, '").append(cardsContainerId).append("')\">");
                html.append("<option value='updated' selected>Recenti</option><option value='commit'>Ultimo Commit</option><option value='name'>A-Z</option>");
                html.append("</select></div>");

                html.append("<div id='").append(cardsContainerId).append("' class='cards-container'>");
                for (int j = 0; j < catList.size(); j++) {
                    Object[] rData = (Object[]) catList.get(j);
                    String rTitle = (String) rData[0];
                    List rLines = (List) rData[1];
                    String rRepoName=""; String rOwner=""; String rUpdatedTs="0"; String rCommitTs="0";
                    String urlRepo=""; String urlPages=""; String rLang=""; String rMetrics=""; String rIssues="0"; String rHealth=""; String rAuthor=""; String rCommit=""; String rMod=""; String rDeploy="";
                    for (int k = 0; k < rLines.size(); k++) {
                        String repoLine = (String) rLines.get(k);
                        int colonIdx = repoLine.indexOf(":");
                        if (colonIdx > 0 && repoLine.length() > colonIdx + 1) {
                            String prefix = repoLine.substring(0, colonIdx).trim();
                            String rest = repoLine.substring(colonIdx + 1).trim();
                            if (prefix.equals("Repo")) urlRepo = rest; else if (prefix.equals("Pages")) urlPages = rest; else if (prefix.equals("Linguaggio")) rLang = rest; else if (prefix.equals("Metriche")) rMetrics = rest; else if (prefix.equals("Issues")) rIssues = rest; else if (prefix.equals("Stato")) rHealth = rest; else if (prefix.equals("Deploy")) rDeploy = rest; else if (prefix.equals("Autore")) rAuthor = rest; else if (prefix.equals("Commit")) rCommit = rest; else if (prefix.equals("Modifica")) rMod = rest;
                            else if (prefix.equals("RepoName")) rRepoName = rest; else if (prefix.equals("Owner")) rOwner = rest; else if (prefix.equals("UpdatedTs")) rUpdatedTs = rest; else if (prefix.equals("CommitTs")) rCommitTs = rest;
                        }
                    }
                    String cssCardColor = "";
                    if (catName.equals("Frontend Mentor")) cssCardColor = "border-left-color: #0369a1;";
                    if (catName.equals("Build Week")) cssCardColor = "border-left-color: #c2410c;";

                    html.append("<div class='card original-card' style='").append(cssCardColor).append("' data-name='").append(rRepoName).append("' data-updated='").append(rUpdatedTs).append("' data-commit='").append(rCommitTs).append("'>");
                    html.append("<div class='repo-header'><div class='repo'>").append(rTitle).append("</div>");
                    html.append("<div class='badges'>");
                    if (rLang.length() > 0 && !rLang.equals("N/D")) html.append("<span class='badge badge-lang'>").append(rLang).append("</span>");
                    if (rHealth.length() > 0) html.append("<span class='badge badge-health'>").append(rHealth).append("</span>");
                    if (rDeploy.length() > 0) { String bClass = "badge-health"; if (rDeploy.contains("Successo")) bClass = "badge-success"; else if (rDeploy.contains("Fallito")) bClass = "badge-danger"; else if (rDeploy.contains("esecuzione")) bClass = "badge-warning"; html.append("<span class='badge ").append(bClass).append("'>").append(rDeploy).append("</span>"); }
                    html.append("</div></div>");
                    if(urlRepo.length() > 0) html.append("<div class='line'><span class='icon'>🔗</span> <div><span class='bold'>Repo:</span> <a href='").append(urlRepo).append("'>Apri GitHub</a></div></div>");
                    if(urlPages.length() > 0) html.append("<div class='line'><span class='icon'>🌐</span> <div><span class='bold'>Pages:</span> <a href='").append(urlPages).append("'>Visita Sito</a></div></div>");
                    if(rAuthor.length() > 0) html.append("<div class='line'><span class='icon'>👤</span> <div><span class='bold'>Autore:</span> ").append(rAuthor).append("</div></div>");
                    if(rCommit.length() > 0) html.append("<div class='line'><span class='icon'>📝</span> <div><span class='bold'>Commit:</span> ").append(rCommit).append("</div></div>");
                    if(rMod.length() > 0) html.append("<div class='line'><span class='icon'>📅</span> <div><span class='bold'>Modifica:</span> ").append(rMod).append("</div></div>");
                    if(rMetrics.length() > 0) html.append("<div class='metrics-bar'>").append(rMetrics).append("</div>");
                    if(urlRepo.length() > 0) {
                        html.append("<div class='actions'>");
                        int issuesNum = 0; try { issuesNum = Integer.parseInt(rIssues); } catch(Exception e){}
                        if(issuesNum > 0) html.append("<a class='btn btn-danger' href='").append(urlRepo).append("/issues'>🐛 Issues (").append(issuesNum).append(")</a>"); else html.append("<a class='btn' href='").append(urlRepo).append("/issues'>🐛 Issues</a>");
                        html.append("<a class='btn' href='").append(urlRepo).append("/pulls'>🔀 PRs</a>");
                        html.append("<button class='btn btn-warning' onclick=\"prepRenameRepo('").append(rOwner).append("', '").append(rRepoName).append("')\">✏️ Rinomina</button>");
                        html.append("<button class='btn btn-primary' onclick=\"copyText('").append(urlRepo).append(".git')\">📋 Clone</button>");
                        html.append("</div>");
                    }
                    html.append("</div>");
                }
                html.append("</div></div>");
            }
        }
    }
}

html.append("<div id='toast'></div>");

html.append("<script>");
html.append("var historyStack = ['view-home'];");
html.append("var GITHUB_TOKEN = '").append(githubToken).append("';");
html.append("var GAS_URL = '").append(baseGasUrl).append("';");
html.append("var APP_VERSION = '").append(appVersion).append("';");
html.append("var UPDATE_JSON_URL = '").append(updateJsonUrl).append("';");
html.append("var currentRenameOwner = ''; var currentRenameName = '';");
html.append("var newCodeUrl = '';");

html.append("function showToast(msg) {");
html.append("  var toast = document.getElementById('toast'); toast.innerText = msg; toast.classList.add('show');");
html.append("  setTimeout(function(){ toast.classList.remove('show'); }, 3500);");
html.append("}");

html.append("function copyText(text) {");
html.append("  var ta = document.createElement('textarea'); ta.value = text; document.body.appendChild(ta); ta.select(); document.execCommand('copy'); document.body.removeChild(ta);");
html.append("  showToast('📋 Copiato!');");
html.append("}");

html.append("function openView(viewId) {");
html.append("  document.getElementById(historyStack[historyStack.length - 1]).classList.remove('active');");
html.append("  historyStack.push(viewId);");
html.append("  document.getElementById(viewId).classList.add('active');");
html.append("  updateUIElements(); window.scrollTo(0,0);");
html.append("}");

html.append("function goBack() {");
html.append("  if (historyStack.length > 1) {");
html.append("    var current = historyStack.pop();");
html.append("    document.getElementById(current).classList.remove('active');");
html.append("    document.getElementById(historyStack[historyStack.length - 1]).classList.add('active');");
html.append("    updateUIElements();");
html.append("  }");
html.append("}");

html.append("function updateUIElements() {");
html.append("  var isHome = (historyStack.length === 1);");
html.append("  document.getElementById('backBtn').style.display = isHome ? 'none' : 'flex';");
html.append("  document.getElementById('navPills').style.display = isHome ? 'flex' : 'none';");
html.append("  if(isHome) { document.getElementById('searchInput').value = ''; }");
html.append("}");

html.append("function sortCards(selectElem, containerId) {");
html.append("  var container = document.getElementById(containerId);");
html.append("  var cards = Array.from(container.querySelectorAll('.card'));");
html.append("  var sortBy = selectElem.value;");
html.append("  cards.sort(function(a, b) {");
html.append("    if(sortBy === 'updated') return parseInt(b.getAttribute('data-updated')) - parseInt(a.getAttribute('data-updated'));");
html.append("    if(sortBy === 'commit') return parseInt(b.getAttribute('data-commit')) - parseInt(a.getAttribute('data-commit'));");
html.append("    return a.getAttribute('data-name').localeCompare(b.getAttribute('data-name'));");
html.append("  });");
html.append("  cards.forEach(function(c) { container.appendChild(c); });");
html.append("}");

html.append("window.onload = function() {");
html.append("  var selects = document.querySelectorAll('.sort-select');");
html.append("  selects.forEach(function(s) { s.dispatchEvent(new Event('change')); });");
html.append("  if(UPDATE_JSON_URL && UPDATE_JSON_URL.startsWith('http')) { checkForUpdates(true); }");
html.append("};");

html.append("function filterRepos() {");
html.append("  var input = document.getElementById('searchInput').value.toUpperCase();");
html.append("  var searchView = document.getElementById('view-search');");
html.append("  var searchResults = document.getElementById('search-results');");
html.append("  var allStandardViews = document.querySelectorAll('.view:not(#view-search)');");
html.append("  if (input === '') {");
html.append("    searchView.classList.remove('active');");
html.append("    document.getElementById(historyStack[historyStack.length - 1]).classList.add('active');");
html.append("    updateUIElements();");
html.append("  } else {");
html.append("    allStandardViews.forEach(function(v) { v.classList.remove('active'); });");
html.append("    document.getElementById('backBtn').style.display = 'none';");
html.append("    document.getElementById('navPills').style.display = 'none';");
html.append("    searchView.classList.add('active'); searchResults.innerHTML = '';");
html.append("    var allCards = document.querySelectorAll('.original-card');");
html.append("    var count = 0;");
html.append("    allCards.forEach(function(card) {");
html.append("      if (card.innerText.toUpperCase().indexOf(input) > -1) {");
html.append("        var clone = card.cloneNode(true); searchResults.appendChild(clone); count++;");
html.append("      }");
html.append("    });");
html.append("    if(count === 0) searchResults.innerHTML = \"<div style='text-align:center; padding: 20px; color:#888;'>Nessuna repository trovata</div>\";");
html.append("  }");
html.append("}");

html.append("async function loadAppInfo() {");
html.append("  openView('view-app-info');");
html.append("  var container = document.getElementById('content-app-info');");
html.append("  var h = \"<div class='card'><div><span class='bold'>Versione Attuale:</span> \" + APP_VERSION + \"</div><br>\";");
html.append("  if (!UPDATE_JSON_URL || !UPDATE_JSON_URL.startsWith('http')) {");
html.append("    h += \"<div>⚠️ URL di aggiornamento non configurato in Tasker. Assicurati di aver impostato la variabile.</div></div>\";");
html.append("    container.innerHTML = h; return;");
html.append("  }");
html.append("  h += \"<div id='updateStatus'>Ricerca aggiornamenti in corso... ⏳</div></div>\";");
html.append("  container.innerHTML = h;");
html.append("  checkForUpdates(false);");
html.append("}");

html.append("async function checkForUpdates(silent) {");
html.append("  try {");
html.append("    let separator = UPDATE_JSON_URL.indexOf('?') !== -1 ? '&' : '?';");
html.append("    let res = await fetch(UPDATE_JSON_URL + separator + 't=' + new Date().getTime());");
html.append("    if(res.ok) {");
html.append("      let data = await res.json();");
html.append("      if(parseFloat(data.version) > parseFloat(APP_VERSION)) {");
html.append("        document.getElementById('updateBadge').style.display = 'block';");
html.append("        if(silent) { showToast('⚠️ Aggiornamento ' + data.version + ' disponibile! Vai in App Info.'); return; }");
html.append("        newCodeUrl = data.code_url;");
html.append("        var statusDiv = document.getElementById('updateStatus');");
html.append("        if(statusDiv) {");
html.append("           var changelogHtml = '';");
html.append("           if (Array.isArray(data.changelog)) {");
html.append("             changelogHtml = \"<ul style='margin:8px 0; padding-left:20px; text-align:left;'><li>\" + data.changelog.join(\"</li><li>\") + \"</li></ul>\";");
html.append("           } else { changelogHtml = data.changelog; }");
html.append("           statusDiv.innerHTML = \"<div><span class='bold' style='color:#dc2626;'>Nuova versione \" + data.version + \" disponibile!</span></div><div class='line'>\" + changelogHtml + \"</div><br><button class='btn-submit btn-update' onclick='downloadUpdate()'>Scarica e Copia Codice</button>\";");
html.append("        }");
html.append("      } else {");
html.append("        var statusDiv = document.getElementById('updateStatus');");
html.append("        if(statusDiv) statusDiv.innerHTML = \"<div style='color:#16a34a;'><span class='bold'>✅ Sei all'ultima versione.</span></div>\";");
html.append("      }");
html.append("    } else { if(!silent) document.getElementById('updateStatus').innerText = '❌ Errore nel controllo aggiornamenti.'; }");
html.append("  } catch(e) { if(!silent) document.getElementById('updateStatus').innerText = '❌ Errore di rete durante il controllo.'; }");
html.append("}");

html.append("async function downloadUpdate() {");
html.append("  try {");
html.append("    showToast('Download in corso... ⏳');");
/* FIX: Gestione corretta dei parametri nell'URL di download */
html.append("    let separator = newCodeUrl.indexOf('?') !== -1 ? '&' : '?';");
html.append("    let res = await fetch(newCodeUrl + separator + 't=' + new Date().getTime());");
html.append("    if(res.ok) {");
html.append("      let rawCode = await res.text();");
html.append("      var ta = document.createElement('textarea'); ta.value = rawCode; document.body.appendChild(ta); ta.select(); document.execCommand('copy'); document.body.removeChild(ta);");
html.append("      showToast('✅ Codice copiato! Incollalo nell\\'azione Java su Tasker.');");
html.append("    } else { showToast('❌ Impossibile scaricare il codice.'); }");
html.append("  } catch(e) { showToast('❌ Errore di rete'); }");
html.append("}");

html.append("async function createRepo() {");
html.append("  var name = document.getElementById('newRepoName').value.trim();");
html.append("  if (!name) { showToast('⚠️ Inserisci un nome!'); return; }");
html.append("  var desc = document.getElementById('newRepoDesc').value.trim();");
html.append("  var isPrivate = document.getElementById('newRepoPrivate').checked;");
html.append("  var initReadme = document.getElementById('newRepoReadme').checked;");
html.append("  var btn = document.getElementById('btnCreate');");
html.append("  btn.innerText = 'Creazione in corso... ⏳'; btn.disabled = true;");
html.append("  try {");
html.append("    let res = await fetch('https://api.github.com/user/repos', { method: 'POST', headers: { 'Authorization': 'token ' + GITHUB_TOKEN, 'Accept': 'application/vnd.github.v3+json', 'Content-Type': 'application/json' }, body: JSON.stringify({ name: name, description: desc, private: isPrivate, auto_init: initReadme }) });");
html.append("    if (res.ok) {");
html.append("      fetch(GAS_URL + '&clear_cache=true&token=' + GITHUB_TOKEN, { mode: 'no-cors' });");
html.append("      showToast('✅ Creata! Riavvia la Dashboard.');");
html.append("      setTimeout(function() { document.getElementById('newRepoName').value = ''; goBack(); }, 2000);");
html.append("    } else { let err = await res.json(); showToast('❌ Errore: ' + err.message); }");
html.append("  } catch (e) { showToast('❌ Errore di rete'); }");
html.append("  btn.innerText = 'Crea Repository'; btn.disabled = false;");
html.append("}");

html.append("function prepRenameRepo(owner, currentName) {");
html.append("  currentRenameOwner = owner; currentRenameName = currentName;");
html.append("  document.getElementById('renameRepoInput').value = currentName;");
html.append("  openView('view-rename-repo');");
html.append("}");

html.append("async function executeRename() {");
html.append("  var newName = document.getElementById('renameRepoInput').value.trim();");
html.append("  if (!newName) { showToast('⚠️ Inserisci un nome valido!'); return; }");
html.append("  if (newName === currentRenameName) { showToast('⚠️ Il nome non è cambiato.'); return; }");
html.append("  var btn = document.getElementById('btnRename');");
html.append("  btn.innerText = 'Rinominazione... ⏳'; btn.disabled = true;");
html.append("  try {");
html.append("    let res = await fetch(`https://api.github.com/repos/${currentRenameOwner}/${currentRenameName}`, { method: 'PATCH', headers: { 'Authorization': 'token ' + GITHUB_TOKEN, 'Accept': 'application/vnd.github.v3+json', 'Content-Type': 'application/json' }, body: JSON.stringify({ name: newName }) });");
html.append("    if (res.ok) {");
html.append("      fetch(GAS_URL + '&clear_cache=true&token=' + GITHUB_TOKEN, { mode: 'no-cors' });");
html.append("      showToast('✅ Rinominata! Riavvia la Dashboard.');");
html.append("      setTimeout(function() { goBack(); }, 2000);");
html.append("    } else { let err = await res.json(); showToast('❌ Errore: ' + err.message); }");
html.append("  } catch (e) { showToast('❌ Errore di rete'); }");
html.append("  btn.innerText = 'Rinomina'; btn.disabled = false;");
html.append("}");

html.append("async function loadNavView(viewName) {");
html.append("  openView('view-' + viewName);");
html.append("  var container = document.getElementById('content-' + viewName);");
html.append("  container.innerHTML = \"<div class='loader'></div>\";");
html.append("  try {");
html.append("    let headers = { 'Authorization': 'token ' + GITHUB_TOKEN, 'Accept': 'application/vnd.github.v3+json' };");
html.append("    if (viewName === 'profile') {");
html.append("      let res = await fetch('https://api.github.com/user', {headers}); let data = await res.json();");
html.append("      container.innerHTML = `<div class='card' style='text-align:center; border:none;'><img src='${data.avatar_url}' class='profile-img'><h2>${data.name || data.login}</h2><p>${data.bio || ''}</p><div class='metrics-bar' style='display:flex; justify-content:space-around;'><span>👤 Followers: ${data.followers}</span><span>👥 Following: ${data.following}</span></div><a class='btn btn-primary' style='display:block;margin-top:10px;' href='${data.html_url}'>Apri GitHub</a></div>`;");
html.append("    } else if (viewName === 'notifications') {");
html.append("      let res = await fetch('https://api.github.com/notifications', {headers}); let data = await res.json();");
html.append("      if(data.length === 0) { container.innerHTML = \"<div class='card' style='text-align:center; padding: 40px;'><h2>🎉</h2><p>Nessuna notifica!</p></div>\"; } else {");
html.append("        let h = \"<div class='card' style='padding:0;'>\";");
html.append("        data.forEach(n => h += `<div class='notif-item'><div class='bold'>${n.subject.title}</div><div class='line'>${n.repository.full_name}</div></div>`);");
html.append("        container.innerHTML = h + \"</div>\";");
html.append("      }");
html.append("    } else if (viewName === 'explore') {");
html.append("      let res = await fetch('https://api.github.com/search/repositories?q=stars:>50000&sort=stars&order=desc&per_page=15', {headers}); let data = await res.json();");
html.append("      let h = \"<div class='cards-container'>\";");
html.append("      data.items.forEach(r => h += `<div class='card'><div class='bold' style='font-size:16px;margin-bottom:8px;'><a href='${r.html_url}'>${r.full_name}</a></div><div class='line' style='margin-bottom:12px;'>${r.description || ''}</div><div class='badge badge-warning' style='width:fit-content;'>⭐ ${r.stargazers_count}</div></div>`);");
html.append("      container.innerHTML = h + \"</div>\";");
html.append("    }");
html.append("  } catch(e) { container.innerHTML = \"<div class='card'>Errore.</div>\"; }");
html.append("}");

html.append("</script></body></html>");

activity.runOnUiThread(new Runnable() {
    run() { webView.loadDataWithBaseURL("https://app.githubmanager/", html.toString(), "text/html", "UTF-8", null); }
});