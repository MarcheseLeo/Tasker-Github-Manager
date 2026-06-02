import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.util.function.Consumer;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.graphics.Color;
import android.view.WindowManager;
import android.view.ViewGroup;
import io.reactivex.subjects.SingleSubject;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.Collections;

/* ⚠️ CONFIGURAZIONE APP ⚠️ */
String githubToken = "%token";
String baseGasUrl = "%GAS_URL";
String updateJsonUrl = "%update_url"; 
String appVersion = "1.6.2"; /* 📦 FIX CARTELLE & DETTAGLI */

uiReadySignal = SingleSubject.create();
dialogClosedSignal = SingleSubject.create();

String loadingHtml = "<html><body style='background:#121212; display:flex; justify-content:center; align-items:center; height:100vh; margin:0;'><div style='width:40px;height:40px;border:4px solid #333;border-top:4px solid #1db954;border-radius:50%;animation:spin 1s linear infinite;'></div><style>@keyframes spin { 100% { transform:rotate(360deg); } }</style></body></html>";

consumer = new Consumer() {
    accept(Object activityObj) {
        Activity currentActivity = (Activity) activityObj;
        WebView loaderWebView = new WebView(currentActivity);
        loaderWebView.setBackgroundColor(Color.parseColor("#121212"));
        loaderWebView.getSettings().setJavaScriptEnabled(true);
        loaderWebView.getSettings().setDomStorageEnabled(true);
        loaderWebView.setFocusable(true);
        loaderWebView.setFocusableInTouchMode(true);
        
        final Dialog dialog = new Dialog(currentActivity, android.R.style.Theme_DeviceDefault_NoActionBar);
        dialog.setContentView(loaderWebView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        
        dialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            onKey(DialogInterface d, int keyCode, android.view.KeyEvent event) {
                if (keyCode == 4 && event.getAction() == 1) { 
                    if (loaderWebView.canGoBack()) {
                        loaderWebView.goBack(); 
                        return true;
                    } else {
                        dialog.dismiss();
                        dialogClosedSignal.onSuccess("closed");
                        currentActivity.finish();
                        return true;
                    }
                }
                return false;
            }
        });

        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            onCancel(DialogInterface d) { dialogClosedSignal.onSuccess("cancelled"); currentActivity.finish(); }
        });
        
        loaderWebView.loadDataWithBaseURL("https://app.githubmanager/", loadingHtml, "text/html", "UTF-8", null);
        dialog.show();
        dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        loaderWebView.requestFocus();

        uiReadySignal.onSuccess(new Object[]{currentActivity, loaderWebView});
    }
};

tasker.doWithActivity(consumer);
Object[] uiObjects = (Object[]) uiReadySignal.blockingGet();
Activity activity = (Activity) uiObjects[0];
WebView webView = (WebView) uiObjects[1];

OkHttpClient client = new OkHttpClient.Builder().connectTimeout(60, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build();
String requestUrl = baseGasUrl + "?token=" + githubToken;
Request request = new Request.Builder().url(requestUrl).build();

String rawData = "";
try {
    Response response = client.newCall(request).execute();
    rawData = response.body().string();
} catch (Exception e) { rawData = "Errore: " + e.getMessage(); }

String[] lines = rawData.split("\n");
HashMap categoriesMap = new HashMap();
ArrayList categoryOrder = new ArrayList();
categoryOrder.add("Epicode"); categoryOrder.add("Frontend Mentor"); categoryOrder.add("Build Week"); categoryOrder.add("Altre Repo");
for (int i = 0; i < categoryOrder.size(); i++) { categoriesMap.put(categoryOrder.get(i), new ArrayList()); }

String currentCategory = "Altre Repo"; String currentRepoTitle = ""; ArrayList currentRepoLines = new ArrayList(); String currentEpicodeMonth = "Altro";
String pubCount = "0"; String privCount = "0"; String totCount = "0";

for (int i = 0; i < lines.length; i++) {
    String line = lines[i].trim();
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
        currentRepoTitle = line; currentRepoLines = new ArrayList(); currentEpicodeMonth = "Altro";
        String upperLine = line.toUpperCase();
        if (upperLine.contains("FRONTEND-MENTOR") || upperLine.contains("FRONTEND MENTOR")) { currentCategory = "Frontend Mentor"; } 
        else if (upperLine.contains("BUILDWEEK") || upperLine.contains("BUILD WEEK")) { currentCategory = "Build Week"; } 
        else if (upperLine.contains("EPICODE")) {
            currentCategory = "Epicode";
            int mIdx = upperLine.indexOf("-M");
            if (mIdx >= 0 && mIdx + 2 < upperLine.length()) {
                String possibleMonth = upperLine.substring(mIdx + 1, mIdx + 3);
                if (possibleMonth.startsWith("M")) currentEpicodeMonth = possibleMonth; 
            }
        } else { currentCategory = "Altre Repo"; }
    } else if (line.startsWith("-----------------")) {
    } else { if (currentRepoTitle.length() > 0) currentRepoLines.add(line); }
}
if (currentRepoTitle.length() > 0) {
    List catList = (List) categoriesMap.get(currentCategory);
    catList.add(new Object[]{currentRepoTitle, currentRepoLines, currentEpicodeMonth});
}

StringBuilder html = new StringBuilder();
html.append("<!DOCTYPE html><html lang='it'><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'>");
html.append("<style>");
html.append(":root { --bg: #121212; --surface: #181818; --surface-elevated: #282828; --primary: #1db954; --danger: #e91429; --warning: #ffa42b; --text-main: #ffffff; --text-sec: #a7a7a7; --border: #333; --radius: 16px; --radius-sm: 8px; --nav-h: 64px; } ");
html.append("body.theme-amoled { --bg: #000000; --surface: #0a0a0a; --surface-elevated: #141414; --border: #1f1f1f; } ");
html.append("body.theme-light { --bg: #ffffff; --surface: #f8f9fa; --surface-elevated: #ffffff; --text-main: #000000; --text-sec: #555555; --border: #e5e7eb; } ");
html.append("body.palette-0 { --primary: #1db954; } body.palette-1 { --primary: #8b5cf6; } body.palette-2 { --primary: #3b82f6; } body.palette-3 { --primary: #14b8a6; } body.palette-4 { --primary: #f43f5e; } ");
html.append("* { box-sizing: border-box; -webkit-tap-highlight-color: transparent; } ");
html.append("body { font-family: 'Segoe UI', Roboto, sans-serif; margin: 0; padding: 0; background-color: var(--bg); color: var(--text-main); padding-bottom: calc(var(--nav-h) + 20px); overflow-x: hidden; transition: background-color 0.3s; } ");

html.append(".topbar { position: sticky; top: 0; z-index: 50; background: var(--bg); padding-top: 55px; padding-bottom: 12px; transition: background 0.3s; } ");
html.append(".title { font-size: 32px; font-weight: 800; margin: 0; padding: 0 20px; letter-spacing: -0.5px; } ");
html.append(".breadcrumbs { padding: 8px 20px 0 20px; font-size: 16px; font-weight: 700; color: var(--text-sec); display: flex; align-items: center; gap: 8px; overflow-x: auto; white-space: nowrap; } ");
html.append(".breadcrumbs span { cursor: pointer; transition: color 0.2s; } .breadcrumbs span:active { color: var(--primary); } .breadcrumbs .separator { font-size: 14px; opacity: 0.5; } ");

html.append(".search-container { padding: 0 20px; margin-bottom: 16px; margin-top: 10px; } ");
html.append(".search-box { background: var(--surface-elevated); display: flex; align-items: center; padding: 12px 16px; border-radius: 8px; gap: 12px; } ");
html.append(".search-box input { background: transparent; border: none; color: var(--text-main); font-size: 16px; width: 100%; outline: none; } .search-box input::placeholder { color: var(--text-sec); } ");
html.append(".pills-scroll { display: flex; gap: 10px; padding: 0 20px; overflow-x: auto; scrollbar-width: none; margin-bottom: 24px; } .pills-scroll::-webkit-scrollbar { display: none; } ");
html.append(".pill { background: var(--surface-elevated); color: var(--text-main); padding: 8px 16px; border-radius: 20px; font-size: 14px; font-weight: 600; white-space: nowrap; transition: all 0.2s; cursor:pointer; } ");
html.append(".pill.active { background: var(--primary); color: #fff; } ");
html.append(".container { padding: 0 20px; } ");
html.append(".view { display: none; animation: fadeIn 0.2s ease; } .view.active { display: block; } @keyframes fadeIn { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: translateY(0); } } ");

html.append(".folder-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; margin-bottom: 24px; } ");
html.append(".folder-card { background: var(--surface); border-radius: var(--radius); padding: 20px; display: flex; flex-direction: column; justify-content: center; align-items:center; cursor: pointer; aspect-ratio: 1; transition: transform 0.2s; } ");
html.append(".folder-card:active { transform: scale(0.97); background: var(--surface-elevated); } .folder-icon { font-size: 40px; margin-bottom: 12px; } .folder-info { text-align:center; } .folder-title { font-size: 16px; font-weight: 700; line-height: 1.2; } .folder-arrow { display: none; } ");
html.append("body.layout-list .folder-grid { display: flex; flex-direction: column; gap: 12px; } ");
html.append("body.layout-list .folder-card { flex-direction: row; justify-content: flex-start; padding: 16px; aspect-ratio: auto; gap: 16px; } ");
html.append("body.layout-list .folder-info { text-align: left; flex: 1; } body.layout-list .folder-icon { margin-bottom: 0; font-size: 32px; } body.layout-list .folder-arrow { display: block; font-size: 24px; color: var(--text-sec); } ");

html.append(".repo-container { display: flex; flex-direction: column; gap: 12px; margin-bottom:20px; } body.layout-grid .repo-container { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; } ");
html.append(".repo-card { background: var(--surface); border-radius: var(--radius); padding: 16px; position: relative; overflow-wrap: anywhere; cursor:pointer; transition: transform 0.2s;} body.layout-grid .repo-card { padding: 14px; } .repo-card:active { transform: scale(0.98); background: var(--surface-elevated); } ");
html.append(".repo-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; gap:8px;} .repo-name { font-size: 15px; font-weight: 700; color: var(--text-main); } .repo-meta { font-size: 12px; color: var(--text-sec); margin-top: 4px; } ");
html.append(".btn-icon { background: transparent; border: none; color: var(--text-sec); font-size: 22px; padding: 4px; cursor: pointer; transition: color 0.2s; display:flex; align-items:center; justify-content:center;} .btn-icon.active { color: var(--warning); } ");

html.append(".list-group { background: var(--surface); border-radius: var(--radius); overflow: hidden; margin-bottom: 24px; } ");
html.append(".list-item { display: flex; align-items: center; justify-content: space-between; padding: 16px; cursor: pointer; transition: background 0.2s;} .list-item:active { background: var(--surface-elevated); } ");
html.append(".list-item-content { display: flex; align-items: center; gap: 16px; } .list-item-icon { font-size: 24px; color: var(--text-sec); } .list-item-title { font-size: 16px; font-weight: 600; color: var(--text-main); } .list-item-sub { font-size: 13px; color: var(--text-sec); margin-top: 2px; } ");
html.append(".toggle { position: relative; width: 48px; height: 26px; } .toggle input { opacity: 0; width: 0; height: 0; } .slider { position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0; background-color: #444; border-radius: 26px; transition: .3s; } .slider:before { position: absolute; content: ''; height: 18px; width: 18px; left: 4px; bottom: 4px; background-color: #fff; border-radius: 50%; transition: .3s; } input:checked + .slider { background-color: var(--primary); } input:checked + .slider:before { transform: translateX(22px); } ");
html.append(".btn { width: 100%; padding: 16px; border-radius: 24px; font-size: 16px; font-weight: 700; border: none; cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 8px; transition: transform 0.2s; } .btn:active { transform: scale(0.98); } .btn-primary { background: var(--primary); color: #000; } .btn-danger { background: transparent; border: 1px solid var(--danger); color: var(--danger); } ");
html.append(".form-label { display: block; font-size: 14px; font-weight: 600; color: var(--text-sec); margin-bottom: 8px; } .form-input { width: 100%; background: var(--surface-elevated); border: 1px solid var(--border); color: var(--text-main); padding: 14px; border-radius: var(--radius-sm); font-size: 16px; margin-bottom: 20px; outline: none; } .form-input:focus { border-color: var(--primary); } ");

html.append(".bottom-nav { position: fixed; bottom: 0; left: 0; right: 0; height: var(--nav-h); background: var(--bg); display: flex; justify-content: space-around; align-items: center; z-index: 100; padding-bottom: env(safe-area-inset-bottom); transition: background 0.3s; } ");
html.append(".nav-item { display: flex; flex-direction: column; align-items: center; gap: 4px; color: var(--text-sec); font-size: 11px; font-weight: 600; width: 25%; cursor: pointer; position: relative; transition: color 0.2s;} .nav-item.active { color: var(--text-main); } .nav-item .icon { font-size: 24px; } .badge-dot { position: absolute; top: 2px; right: 25%; background: var(--danger); width: 8px; height: 8px; border-radius: 50%; display: none; } ");

html.append(".fab { position: fixed; bottom: calc(var(--nav-h) + 20px); right: 20px; width: 56px; height: 56px; border-radius: 50%; background: var(--primary); color: #000; display: flex; align-items: center; justify-content: center; font-size: 28px; box-shadow: 0 4px 12px rgba(0,0,0,0.3); cursor: pointer; z-index: 99; transition: transform 0.2s; } .fab:active { transform: scale(0.9); } ");
html.append(".fab-menu { position: fixed; bottom: 0; left: 0; right: 0; top: 0; background: rgba(0,0,0,0.6); z-index: 1000; display: none; flex-direction: column; justify-content: flex-end; padding: 20px; } .fab-menu.active { display: flex; animation: fadeIn 0.2s; } .fab-content { background: var(--surface); border-radius: var(--radius); padding: 20px; margin-bottom: var(--nav-h); } ");

html.append(".modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.8); display: none; justify-content: center; align-items: center; z-index: 2000; padding: 20px; } .modal-overlay.active { display: flex; } .modal { background: var(--surface); border-radius: var(--radius); padding: 24px; width: 100%; max-width: 400px; text-align: center; } .modal h2 { margin-top: 0; font-size: 22px; } .modal-body { text-align: left; margin: 16px 0; font-size: 14px; color: var(--text-sec); max-height: 200px; overflow-y: auto; } .modal-actions { display: flex; gap: 12px; margin-top: 24px; } ");
html.append("#toast { position: fixed; bottom: 100px; left: 50%; transform: translateX(-50%) translateY(20px); background: var(--text-main); color: var(--bg); padding: 12px 24px; border-radius: 30px; font-weight: 600; font-size: 14px; opacity: 0; visibility: hidden; transition: all 0.3s; z-index: 9999; white-space: nowrap; } #toast.show { opacity: 1; visibility: visible; transform: translateX(-50%) translateY(0); } ");
html.append(".preview-card { background: var(--surface); border-radius: 16px; padding: 20px; margin-bottom: 24px; display: flex; align-items: center; gap: 16px; } .preview-icon { width: 56px; height: 56px; background: var(--primary); border-radius: 12px; display: flex; align-items: center; justify-content: center; font-size: 24px; color: #000; } .preview-lines { flex: 1; } .preview-line-1 { height: 12px; width: 80%; background: var(--text-main); border-radius: 6px; margin-bottom: 8px; } .preview-line-2 { height: 10px; width: 50%; background: var(--text-sec); border-radius: 5px; } ");
html.append(".segmented-control { display: flex; background: var(--surface); border-radius: 12px; padding: 4px; gap: 4px; margin-bottom:24px;} .segmented-btn { flex: 1; display: flex; flex-direction: column; align-items: center; padding: 12px 8px; border-radius: 8px; font-size: 13px; font-weight: 600; color: var(--text-sec); cursor: pointer; transition: all 0.2s; } .segmented-btn.active { background: var(--surface-elevated); color: var(--primary); } .segmented-btn .icon { font-size: 20px; margin-bottom: 4px; } ");
html.append(".color-circle { width: 44px; height: 44px; border-radius: 16px; cursor: pointer; transition: transform 0.2s; display: flex; align-items: center; justify-content: center; font-size: 20px; color: #fff; } .color-circle.active { transform: scale(1.1); border: 2px solid var(--text-main); } ");
html.append(".badges { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px;} .badge-txt { font-size: 10px; font-weight: 700; padding: 4px 8px; border-radius: 12px; } .badge-lang { background: var(--surface-elevated); color: var(--text-main); } .badge-success { background: rgba(29, 185, 84, 0.2); color: #1db954; } .badge-danger { background: rgba(233, 20, 41, 0.2); color: #e91429; } .badge-warning { background: rgba(255, 164, 43, 0.2); color: #ffa42b; } ");

html.append(".dashboard-stats { display: flex; justify-content: space-around; background: var(--surface); padding: 12px; border-radius: 12px; margin-bottom: 16px; }");
html.append(".stat-box { text-align: center; } .stat-title { font-size: 11px; color: var(--text-sec); font-weight: 700; text-transform: uppercase; letter-spacing: 0.5px; margin-bottom:4px;} .stat-num { font-size: 20px; font-weight: 800; color: var(--text-main); }");

html.append("</style></head><body>");

if (rawData.startsWith("Errore")) {
    html.append("<div class='container' style='color:var(--danger); padding:60px; text-align:center;'>").append(rawData).append("</div></body></html>");
} else {
    /* TOPBAR E STRUTTURA DI BASE */
    html.append("<div class='topbar'>");
    html.append("  <h1 class='title' id='mainTitle'>Home</h1>");
    html.append("  <div class='breadcrumbs' id='breadcrumbs'></div>");
    html.append("</div>");

    html.append("<div id='main-content'>");
    
    html.append("<div id='view-home' class='view active'>");
    html.append("<div class='search-container'><div class='search-box'><span>🔍</span><input type='text' id='searchInput' placeholder='Search repositories...' onkeyup='filterHomeRepos()'></div></div>");
    
    html.append("<div class='pills-scroll' id='homePills'>");
    html.append("<div class='pill active' onclick=\"setFilter('All', this)\">All</div>");
    html.append("<div class='pill' onclick=\"setFilter('Favorites', this)\">Favorites</div>");
    html.append("<div class='pill' onclick=\"setFilter('Recent', this)\">Recent</div>");
    html.append("</div>");

    html.append("<div class='container' id='home-folders'><div class='folder-grid'>");
    for (int i = 0; i < categoryOrder.size(); i++) {
        String catName = (String) categoryOrder.get(i);
        List catList = (List) categoriesMap.get(catName);
        if (catList.size() > 0) {
            String icon = "📁"; if (catName.equals("Epicode")) icon = "🟣"; if (catName.equals("Frontend Mentor")) icon = "🔵"; if (catName.equals("Build Week")) icon = "🟠";
            String viewId = "view-" + catName.replace(" ", "");
            html.append("<div class='folder-card' onclick=\"openInternalView('").append(viewId).append("', '").append(catName).append("')\"><div class='folder-icon'>").append(icon).append("</div><div class='folder-info'><div class='folder-title'>").append(catName).append("</div><div class='repo-meta'>").append(catList.size()).append(" Repo</div></div><div class='folder-arrow'>›</div></div>");
        }
    }
    html.append("</div></div>");
    
    html.append("<div class='container repo-container' id='home-filtered-repos' style='display:none;'></div>");
    html.append("<div class='fab' onclick=\"document.getElementById('fabMenu').classList.add('active')\">+</div>");
    html.append("</div>"); 

    /* VISTE STATICHE */
    html.append("<div id='view-notifications' class='view'><div class='container' id='content-notifications'></div></div>");
    html.append("<div id='view-explore' class='view'><div class='container' id='content-explore'></div></div>");
    
    html.append("<div id='view-settings' class='view'><div class='container'>");
    html.append("<div class='list-group' id='settings-profile' onclick=\"loadProfileAPI()\"><div class='list-item'><div class='list-item-content'><div class='list-item-icon'>👤</div><div><div class='list-item-title'>Il tuo Profilo</div><div class='list-item-sub'>Stats e followers</div></div></div><div class='list-item-icon' style='font-size:18px;'>›</div></div></div>");
    html.append("<div class='list-group'>");
    html.append("<div class='list-item' onclick=\"openInternalView('view-appearance', 'Appearance')\"><div class='list-item-content'><div class='list-item-icon'>🎨</div><div><div class='list-item-title'>Appearance</div><div class='list-item-sub'>Theme, colors, layout</div></div></div><div class='list-item-icon' style='font-size:18px;'>›</div></div>");
    html.append("<div class='list-item' onclick=\"openInternalView('view-about', 'About')\"><div class='list-item-content'><div class='list-item-icon'>ℹ️</div><div><div class='list-item-title'>Version & About</div><div class='list-item-sub' id='version-sub'>v").append(appVersion).append(" • Clicca per info</div></div></div><div class='list-item-icon' id='settingsVersionBadge' style='color:var(--danger); font-weight:bold; font-size:24px; display:none;'>•</div></div>");
    html.append("</div></div></div>");

    html.append("<div id='view-appearance' class='view'><div class='container'>");
    html.append("<div class='preview-card'><div class='preview-icon'>📁</div><div class='preview-lines'><div class='preview-line-1'></div><div class='preview-line-2'></div></div><div><div class='toggle' style='transform:scale(0.8);'><input type='checkbox' checked disabled><span class='slider'></span></div></div></div>");
    html.append("<div class='form-label'>COLOR</div><div class='list-group' style='margin-bottom:8px;'><div class='list-item'><div class='list-item-content'><div class='list-item-icon'>🎨</div><div><div class='list-item-title'>Dynamic Color</div><div class='list-item-sub'>Use custom color palette</div></div></div><label class='toggle'><input type='checkbox' id='togDynColor' onchange='updateAppearance()'><span class='slider'></span></label></div></div>");
    html.append("<div class='pills-scroll' id='paletteRow' style='padding:0; margin-bottom:24px; transition:opacity 0.2s;'><div class='color-circle' id='btnPalette-0' style='background:#1db954;' onclick=\"setPalette(0)\"></div><div class='color-circle' id='btnPalette-1' style='background:#8b5cf6;' onclick=\"setPalette(1)\"></div><div class='color-circle' id='btnPalette-2' style='background:#3b82f6;' onclick=\"setPalette(2)\"></div><div class='color-circle' id='btnPalette-3' style='background:#14b8a6;' onclick=\"setPalette(3)\"></div><div class='color-circle' id='btnPalette-4' style='background:#f43f5e;' onclick=\"setPalette(4)\"></div></div>");
    html.append("<div class='form-label'>THEME</div><div class='segmented-control'><div class='segmented-btn theme-btn' id='btnTheme-system' onclick=\"setTheme('system')\"><div class='icon'>🅰️</div>System</div><div class='segmented-btn theme-btn' id='btnTheme-light' onclick=\"setTheme('light')\"><div class='icon'>☀️</div>Light</div><div class='segmented-btn theme-btn' id='btnTheme-dark' onclick=\"setTheme('dark')\"><div class='icon'>🌙</div>Dark</div></div>");
    html.append("<div class='list-group'><div class='list-item'><div class='list-item-content'><div class='list-item-icon'>🌘</div><div><div class='list-item-title'>AMOLED Dark</div><div class='list-item-sub'>Pure black background</div></div></div><label class='toggle'><input type='checkbox' id='togAmoled' onchange='updateAppearance()'><span class='slider'></span></label></div></div>");
    html.append("<div class='form-label'>LANGUAGE</div><div class='list-group'><div class='list-item'><div class='list-item-content'><div class='list-item-icon'>🌐</div><div><div class='list-item-title'>App Language</div><div class='list-item-sub'>System Default</div></div></div><div class='list-item-icon' style='font-size:18px;'>›</div></div></div>");
    html.append("<div class='form-label'>LAYOUT</div><div class='segmented-control'><div class='segmented-btn layout-btn' id='btnLayout-list' onclick=\"setLayout('list')\"><div class='icon'>🟰</div>List</div><div class='segmented-btn layout-btn' id='btnLayout-grid' onclick=\"setLayout('grid')\"><div class='icon'>🔲</div>Grid</div></div>");
    html.append("</div></div>");

    html.append("<div id='view-about' class='view'><div class='container' style='text-align:center;'><div style='width:80px;height:80px;background:var(--primary);border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:40px;margin:20px auto;color:#000;'>⬇️</div><h2>GitHub Manager</h2><div class='pill' style='margin: 0 auto 24px auto; width:fit-content; background:var(--surface-elevated);' id='aboutVersionTxt'>v").append(appVersion).append("</div><p style='color:var(--text-sec); font-size:14px;'>Search repos, manage visibility, and organize folders.</p><div class='form-label' style='text-align:left; margin-top:32px;'>UPDATES</div><div class='list-group' style='text-align:left;'><div class='list-item' onclick='checkForUpdates(false)'><div class='list-item-content'><div class='list-item-icon'>🔄</div><div><div class='list-item-title'>Check for Updates</div><div class='list-item-sub' id='aboutUpdateStatus'>Tap to scan</div></div></div></div></div></div></div>");

    html.append("<div id='view-create-repo' class='view'><div class='container'><div class='form-label'>NOME REPOSITORY *</div><input type='text' id='newRepoName' class='form-input' placeholder='Es. frontend-mentor-calc'><div class='form-label'>DESCRIZIONE</div><input type='text' id='newRepoDesc' class='form-input' placeholder='Es. Progetto HTML'><div class='list-group'><div class='list-item'><div class='list-item-title'>Repository Privata 🔒</div><label class='toggle'><input type='checkbox' id='newRepoPrivate'><span class='slider'></span></label></div><div class='list-item'><div class='list-item-title'>Aggiungi README.md 📄</div><label class='toggle'><input type='checkbox' id='newRepoReadme' checked><span class='slider'></span></label></div></div><button id='btnCreate' class='btn btn-primary' onclick='createRepo()'>Crea Repository</button></div></div>");

    html.append("<div id='view-repo-settings' class='view'><div class='container'><div class='form-label'>GENERAL</div><div class='list-group'><div class='list-item'><div style='width:100%;'><input type='text' id='rs-rename-input' class='form-input' style='margin-bottom:12px;'><button class='btn btn-primary' onclick='executeRename()'>Rinomina Repo</button></div></div></div><div class='form-label' style='color:var(--danger);'>DANGER ZONE</div><div class='list-group'><div class='list-item' onclick='executeToggleVisibility()'><div class='list-item-content'><div class='list-item-icon' style='color:var(--danger);'>👁️</div><div><div class='list-item-title' style='color:var(--danger);'>Cambia Visibilità</div><div class='list-item-sub' id='rs-vis-sub'>Imposta come Privata/Pubblica</div></div></div></div><div class='list-item' onclick='executeDeleteRepo()'><div class='list-item-content'><div class='list-item-icon' style='color:var(--danger);'>🗑️</div><div><div class='list-item-title' style='color:var(--danger);'>Elimina Repository</div><div class='list-item-sub'>Azione irreversibile</div></div></div></div></div></div></div>");

    html.append("<div id='view-repo-details' class='view'><div class='container'>");
    html.append("<div style='display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:16px;'><h2 id='det-title' style='margin:0; word-break:break-word; font-size:24px;'>RepoName</h2><button id='det-btn-fav' class='btn-icon fav-btn'>☆</button></div>");
    html.append("<div class='badges' id='det-badges' style='margin-bottom:24px;'></div>");
    html.append("<div class='list-group'><div class='list-item' style='cursor:default;'><div class='list-item-content'><div class='list-item-icon'>📝</div><div><div class='list-item-sub'>Ultimo Commit</div><div class='list-item-title' id='det-commit'>Init</div><div class='list-item-sub' id='det-author' style='color:var(--primary); font-weight:600;'>Autore</div></div></div></div></div>");
    html.append("<div class='dashboard-stats' style='margin-bottom:24px;'><div class='stat-box'><div class='stat-title'>Issues</div><div class='stat-num' id='det-issues'>0</div></div><div class='stat-box'><div class='stat-title'>Metrics</div><div class='stat-num' id='det-metrics' style='font-size:14px; margin-top:8px;'>-</div></div></div>");
    html.append("<div class='form-label'>LINKS & AZIONI</div><div class='list-group'>");
    html.append("<div class='list-item' id='det-link-repo'><div class='list-item-content'><div class='list-item-icon'>🔗</div><div class='list-item-title'>Apri su GitHub</div></div><div class='list-item-icon' style='font-size:18px;'>›</div></div>");
    html.append("<div class='list-item' id='det-link-pages' style='display:none;'><div class='list-item-content'><div class='list-item-icon'>🌐</div><div class='list-item-title'>Visita GitHub Pages</div></div><div class='list-item-icon' style='font-size:18px;'>›</div></div>");
    html.append("<div class='list-item' id='det-link-settings'><div class='list-item-content'><div class='list-item-icon'>⚙️</div><div><div class='list-item-title'>Impostazioni Repo</div><div class='list-item-sub'>Visibilità, Rinomina, Elimina</div></div></div><div class='list-item-icon' style='font-size:18px;'>›</div></div>");
    html.append("</div></div></div>");

    /* GENERAZIONE CARTELLE E REPO DAL JAVA NATIVO (NO JAVASCRIPT TEMPLATES) */
    StringBuilder jsDb = new StringBuilder();
    jsDb.append("var repoDB = {};\n");

    for (int i = 0; i < categoryOrder.size(); i++) {
        String catName = (String) categoryOrder.get(i);
        List catList = (List) categoriesMap.get(catName);
        if (catList.size() > 0) {
            String viewId = "view-" + catName.replace(" ", "");
            
            if (catName.equals("Epicode")) {
                html.append("<div id='").append(viewId).append("' class='view'><div class='container'><div class='folder-grid'>");
                List mesi = new ArrayList();
                for(int j = 0; j < catList.size(); j++) {
                    Object[] rData = (Object[]) catList.get(j);
                    String mese = (String) rData[2];
                    if (!mesi.contains(mese)) { mesi.add(mese); }
                }
                Collections.sort(mesi);
                for(int m = 0; m < mesi.size(); m++) {
                    String meseCorrente = (String) mesi.get(m);
                    int repoCount = 0;
                    for(int j = 0; j < catList.size(); j++) { if (((String)((Object[])catList.get(j))[2]).equals(meseCorrente)) repoCount++; }
                    String subViewId = "view-epicode-" + meseCorrente;
                    html.append("<div class='folder-card' onclick=\"openInternalView('").append(subViewId).append("', 'Modulo ").append(meseCorrente).append("')\"><div class='folder-icon'>🗓️</div><div class='folder-info'><div class='folder-title'>Modulo ").append(meseCorrente).append("</div><div class='repo-meta'>").append(repoCount).append(" Repo</div></div><div class='folder-arrow'>›</div></div>");
                }
                html.append("</div></div></div>");

                for(int m = 0; m < mesi.size(); m++) {
                    String meseCorrente = (String) mesi.get(m);
                    String subViewId = "view-epicode-" + meseCorrente;
                    html.append("<div id='").append(subViewId).append("' class='view'><div class='container repo-container'>");
                    
                    for(int j = 0; j < catList.size(); j++) {
                        Object[] rData = (Object[]) catList.get(j);
                        if (((String)rData[2]).equals(meseCorrente)) {
                            String rTitle = (String) rData[0]; List rLines = (List) rData[1];
                            String rRepoName=""; String rOwner=""; String isPriv="false"; String rUpdatedTs="0"; String rLang=""; String rDeploy="";
                            String urlRepo=""; String urlPages=""; String rMetrics=""; String rIssues="0"; String rHealth=""; String rAuthor=""; String rCommit=""; String rMod="";
                            
                            for (int k = 0; k < rLines.size(); k++) {
                                String repoLine = (String) rLines.get(k); int colonIdx = repoLine.indexOf(":");
                                if (colonIdx > 0) {
                                    String prefix = repoLine.substring(0, colonIdx).trim(); String rest = repoLine.substring(colonIdx + 1).trim();
                                    if (prefix.equals("RepoName")) rRepoName = rest; else if (prefix.equals("Owner")) rOwner = rest; else if (prefix.equals("UpdatedTs")) rUpdatedTs = rest; else if (prefix.equals("Linguaggio")) rLang = rest; else if (prefix.equals("Deploy")) rDeploy = rest;
                                    else if (prefix.equals("Repo")) urlRepo = rest; else if (prefix.equals("Pages")) urlPages = rest; else if (prefix.equals("Metriche")) rMetrics = rest; else if (prefix.equals("Issues")) rIssues = rest; else if (prefix.equals("Stato")) rHealth = rest; else if (prefix.equals("Autore")) rAuthor = rest; else if (prefix.equals("Commit")) rCommit = rest; else if (prefix.equals("Modifica")) rMod = rest;
                                }
                            }
                            if(rTitle.contains("Privata")) isPriv="true";
                            
                            html.append("<div class='repo-card original-card' data-name='").append(rRepoName).append("' data-updated='").append(rUpdatedTs).append("' onclick=\"openRepoDetails('").append(rRepoName).append("')\">");
                            html.append("<div class='repo-header'><div class='repo-name'>").append(rRepoName).append("</div>");
                            html.append("<div style='display:flex;'><button class='btn-icon fav-btn' onclick=\"event.stopPropagation(); toggleFavorite('").append(rRepoName).append("', this)\">☆</button>");
                            html.append("<button class='btn-icon' onclick=\"event.stopPropagation(); openRepoSettings('").append(rOwner).append("','").append(rRepoName).append("','").append(isPriv).append("')\">⋮</button></div></div>");
                            html.append("<div class='badges'>");
                            if (rLang.length() > 0 && !rLang.equals("N/D")) html.append("<span class='badge-txt badge-lang'>").append(rLang).append("</span>");
                            if (rDeploy.length() > 0) { String bClass = "badge-health"; if (rDeploy.contains("Successo")) bClass = "badge-success"; else if (rDeploy.contains("Fallito")) bClass = "badge-danger"; else if (rDeploy.contains("esecuzione")) bClass = "badge-warning"; html.append("<span class='badge-txt ").append(bClass).append("'>").append(rDeploy).append("</span>"); }
                            html.append("</div></div>");
                            
                            String safeAuthor = rAuthor.replace("'", "\\'").replace("\n", " ");
                            String safeCommit = rCommit.replace("'", "\\'").replace("\n", " ");
                            jsDb.append("repoDB['").append(rRepoName).append("'] = { owner: '").append(rOwner).append("', isPriv: '").append(isPriv).append("', url: '").append(urlRepo).append("', pages: '").append(urlPages).append("', lang: '").append(rLang).append("', metrics: '").append(rMetrics).append("', issues: '").append(rIssues).append("', author: '").append(safeAuthor).append("', commit: '").append(safeCommit).append("', modifica: '").append(rMod).append("', health: '").append(rHealth).append("', deploy: '").append(rDeploy).append("' };\n");
                        }
                    }
                    html.append("</div></div>");
                }
            } else {
                html.append("<div id='").append(viewId).append("' class='view'><div class='container repo-container'>");
                for (int j = 0; j < catList.size(); j++) {
                    Object[] rData = (Object[]) catList.get(j);
                    String rTitle = (String) rData[0]; List rLines = (List) rData[1];
                    String rRepoName=""; String rOwner=""; String isPriv="false"; String rUpdatedTs="0"; String rLang=""; String rDeploy="";
                    String urlRepo=""; String urlPages=""; String rMetrics=""; String rIssues="0"; String rHealth=""; String rAuthor=""; String rCommit=""; String rMod="";
                    
                    for (int k = 0; k < rLines.size(); k++) {
                        String repoLine = (String) rLines.get(k); int colonIdx = repoLine.indexOf(":");
                        if (colonIdx > 0) {
                            String prefix = repoLine.substring(0, colonIdx).trim(); String rest = repoLine.substring(colonIdx + 1).trim();
                            if (prefix.equals("RepoName")) rRepoName = rest; else if (prefix.equals("Owner")) rOwner = rest; else if (prefix.equals("UpdatedTs")) rUpdatedTs = rest; else if (prefix.equals("Linguaggio")) rLang = rest; else if (prefix.equals("Deploy")) rDeploy = rest;
                            else if (prefix.equals("Repo")) urlRepo = rest; else if (prefix.equals("Pages")) urlPages = rest; else if (prefix.equals("Metriche")) rMetrics = rest; else if (prefix.equals("Issues")) rIssues = rest; else if (prefix.equals("Stato")) rHealth = rest; else if (prefix.equals("Autore")) rAuthor = rest; else if (prefix.equals("Commit")) rCommit = rest; else if (prefix.equals("Modifica")) rMod = rest;
                        }
                    }
                    if(rTitle.contains("Privata")) isPriv="true";
                    
                    html.append("<div class='repo-card original-card' data-name='").append(rRepoName).append("' data-updated='").append(rUpdatedTs).append("' onclick=\"openRepoDetails('").append(rRepoName).append("')\">");
                    html.append("<div class='repo-header'><div class='repo-name'>").append(rRepoName).append("</div>");
                    html.append("<div style='display:flex;'><button class='btn-icon fav-btn' onclick=\"event.stopPropagation(); toggleFavorite('").append(rRepoName).append("', this)\">☆</button>");
                    html.append("<button class='btn-icon' onclick=\"event.stopPropagation(); openRepoSettings('").append(rOwner).append("','").append(rRepoName).append("','").append(isPriv).append("')\">⋮</button></div></div>");
                    html.append("<div class='badges'>");
                    if (rLang.length() > 0 && !rLang.equals("N/D")) html.append("<span class='badge-txt badge-lang'>").append(rLang).append("</span>");
                    if (rDeploy.length() > 0) { String bClass = "badge-health"; if (rDeploy.contains("Successo")) bClass = "badge-success"; else if (rDeploy.contains("Fallito")) bClass = "badge-danger"; else if (rDeploy.contains("esecuzione")) bClass = "badge-warning"; html.append("<span class='badge-txt ").append(bClass).append("'>").append(rDeploy).append("</span>"); }
                    html.append("</div></div>");
                    
                    String safeAuthor = rAuthor.replace("'", "\\'").replace("\n", " ");
                    String safeCommit = rCommit.replace("'", "\\'").replace("\n", " ");
                    jsDb.append("repoDB['").append(rRepoName).append("'] = { owner: '").append(rOwner).append("', isPriv: '").append(isPriv).append("', url: '").append(urlRepo).append("', pages: '").append(urlPages).append("', lang: '").append(rLang).append("', metrics: '").append(rMetrics).append("', issues: '").append(rIssues).append("', author: '").append(safeAuthor).append("', commit: '").append(safeCommit).append("', modifica: '").append(rMod).append("', health: '").append(rHealth).append("', deploy: '").append(rDeploy).append("' };\n");
                }
                html.append("</div></div>");
            }
        }
    }

    html.append("</div>"); // Chiusura main-content

    /* OVERLAYS E SCRIPT JAVASCRIPT LOGICO */
    html.append("<div id='fabMenu' class='fab-menu' onclick=\"if(event.target===this) this.classList.remove('active')\"><div class='fab-content'><h3 style='margin-top:0; margin-bottom:16px;'>Azioni Rapide</h3><div class='list-group' style='margin:0;'><div class='list-item' onclick=\"document.getElementById('fabMenu').classList.remove('active'); openInternalView('view-create-repo', 'Nuova Repo')\"><div class='list-item-content'><div class='list-item-icon'>➕</div><div class='list-item-title'>Aggiungi Repository</div></div></div></div></div></div>");
    html.append("<div id='updateModal' class='modal-overlay'><div class='modal'><h2>🚀 Aggiornamento!</h2><div class='pill' style='margin: 0 auto; width:fit-content; background:var(--primary); color:#000;' id='upd-badge-ver'>v?.?</div><div class='modal-body' id='upd-changelog'></div><div class='modal-actions'><button class='btn' style='background:var(--surface-elevated); color:var(--text-main);' onclick='skipUpdate()'>Annulla</button><button class='btn btn-primary' onclick='downloadUpdate()'>Scarica Codice</button></div></div></div>");
    html.append("<div class='bottom-nav' id='bottomNav'><div class='nav-item active' onclick=\"switchMainTab('view-home', this, 'Home')\"><div class='icon'>🏠</div><span>Home</span></div><div class='nav-item' onclick=\"switchMainTab('view-notifications', this, 'Notifiche'); loadNotifications();\"><div class='icon'>🔔</div><span>Notifiche</span></div><div class='nav-item' onclick=\"switchMainTab('view-explore', this, 'Esplora'); loadExplore();\"><div class='icon'>⭐</div><span>Esplora</span></div><div class='nav-item' onclick=\"switchMainTab('view-settings', this, 'Settings')\"><div class='badge-dot' id='navSettingsBadge'></div><div class='icon'>⚙️</div><span>Settings</span></div></div>");
    html.append("<div id='toast'></div>");

    html.append("<script>");
    html.append(jsDb.toString());
    html.append("var GITHUB_TOKEN = '").append(githubToken).append("'; var GAS_URL = '").append(baseGasUrl).append("'; var APP_VERSION = '").append(appVersion).append("'; var UPDATE_JSON_URL = '").append(updateJsonUrl).append("'; var newCodeUrl = ''; ");
    html.append("var appState = JSON.parse(localStorage.getItem('ghm_state')) || { theme: 'system', amoled: true, palette: 0, dynamicColor: false, layout: 'grid', favorites: [] }; ");
    
    html.append("function saveState() { localStorage.setItem('ghm_state', JSON.stringify(appState)); applyAppearance(); } ");
    
    html.append("function applyAppearance() { ");
    html.append("  let isDark = false; if(appState.theme === 'system') { isDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches; } else { isDark = appState.theme === 'dark'; } ");
    html.append("  document.body.classList.remove('theme-light', 'theme-dark', 'theme-amoled', 'palette-0', 'palette-1', 'palette-2', 'palette-3', 'palette-4', 'layout-list', 'layout-grid'); ");
    html.append("  if(isDark) { document.body.classList.add('theme-dark'); if(appState.amoled) document.body.classList.add('theme-amoled'); } else { document.body.classList.add('theme-light'); } ");
    html.append("  document.body.classList.add('layout-' + appState.layout); ");
    html.append("  if(appState.dynamicColor) { document.body.classList.add('palette-' + appState.palette); document.getElementById('paletteRow').style.opacity = '1'; document.getElementById('paletteRow').style.pointerEvents = 'auto'; } else { document.body.classList.add('palette-0'); document.getElementById('paletteRow').style.opacity = '0.3'; document.getElementById('paletteRow').style.pointerEvents = 'none'; } ");
    html.append("  document.getElementById('togDynColor').checked = appState.dynamicColor; ");
    html.append("  document.getElementById('togAmoled').checked = appState.amoled; ");
    html.append("  document.querySelectorAll('.theme-btn').forEach(b => b.classList.remove('active')); document.getElementById('btnTheme-' + appState.theme).classList.add('active'); ");
    html.append("  document.querySelectorAll('.layout-btn').forEach(b => b.classList.remove('active')); document.getElementById('btnLayout-' + appState.layout).classList.add('active'); ");
    html.append("  document.querySelectorAll('.color-circle').forEach(b => b.classList.remove('active')); document.getElementById('btnPalette-' + appState.palette).classList.add('active'); ");
    html.append("} ");

    html.append("function setTheme(t) { appState.theme = t; saveState(); } ");
    html.append("function setLayout(l) { appState.layout = l; saveState(); } ");
    html.append("function setPalette(p) { appState.palette = p; saveState(); } ");
    html.append("function updateAppearance() { appState.dynamicColor = document.getElementById('togDynColor').checked; appState.amoled = document.getElementById('togAmoled').checked; saveState(); } ");

    html.append("function showToast(msg) { var toast = document.getElementById('toast'); toast.innerText = msg; toast.classList.add('show'); setTimeout(() => toast.classList.remove('show'), 3000); } ");
    html.append("var navPath = [{ id: 'view-home', name: 'Home' }]; ");
    
    html.append("function renderBreadcrumbs() { let bc = document.getElementById('breadcrumbs'); if(navPath.length <= 1) { bc.style.display = 'none'; document.getElementById('mainTitle').style.display = 'block'; return; } bc.style.display = 'flex'; document.getElementById('mainTitle').style.display = 'none'; let h = ''; navPath.forEach((step, idx) => { if(idx > 0) h += `<span class='separator'>/</span>`; let c = idx===navPath.length-1?'color:var(--text-main)':''; h += `<span style='${c}' onclick='jumpToHistory(${idx})'>${step.name}</span>`; }); bc.innerHTML = h; } ");
    html.append("function switchMainTab(viewId, element, title) { document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active')); element.classList.add('active'); document.getElementById('mainTitle').innerText = title; navPath = [{ id: viewId, name: title }]; history.pushState({ path: navPath }, '', '#' + viewId); document.querySelectorAll('.view').forEach(v => v.classList.remove('active')); document.getElementById(viewId).classList.add('active'); renderBreadcrumbs(); } ");
    html.append("function openInternalView(viewId, title) { navPath.push({ id: viewId, name: title }); history.pushState({ path: navPath }, '', '#' + viewId); document.querySelectorAll('.view').forEach(v => v.classList.remove('active')); document.getElementById(viewId).classList.add('active'); renderBreadcrumbs(); } ");
    html.append("function jumpToHistory(index) { let goBackSteps = navPath.length - 1 - index; if(goBackSteps > 0) { history.go(-goBackSteps); } } ");
    html.append("function goHome() { jumpToHistory(0); } ");
    html.append("window.addEventListener('popstate', (e) => { if(e.state && e.state.path) { navPath = e.state.path; let activeView = navPath[navPath.length - 1]; document.getElementById('mainTitle').innerText = activeView.name; document.querySelectorAll('.view').forEach(v => v.classList.remove('active')); document.getElementById(activeView.id).classList.add('active'); renderBreadcrumbs(); } else { switchMainTab('view-home', document.querySelector('.nav-item'), 'Home'); } }); ");
    
    html.append("function toggleFavorite(repoName, btn) { let idx = appState.favorites.indexOf(repoName); if(idx > -1) { appState.favorites.splice(idx, 1); if(btn) { btn.classList.remove('active'); btn.innerText='☆'; } showToast('Rimosso dai Preferiti'); } else { appState.favorites.push(repoName); if(btn) { btn.classList.add('active'); btn.innerText='⭐'; } showToast('Aggiunto ai Preferiti ⭐'); } saveState(); } ");
    html.append("function setFilter(filterType, element) { document.querySelectorAll('#homePills .pill').forEach(p => p.classList.remove('active')); element.classList.add('active'); let grid = document.getElementById('home-folders'); let list = document.getElementById('home-filtered-repos'); if(filterType === 'All') { grid.style.display = 'grid'; list.style.display = 'none'; } else { grid.style.display = 'none'; list.style.display = 'flex'; list.innerHTML = ''; let cards = document.querySelectorAll('.original-card'); let filtered = []; cards.forEach(c => { let name = c.getAttribute('data-name'); if(filterType === 'Favorites' && appState.favorites.includes(name)) filtered.push(c.cloneNode(true)); else if (filterType === 'Recent') filtered.push(c.cloneNode(true)); }); if(filterType === 'Recent') { filtered.sort((a,b) => b.getAttribute('data-updated') - a.getAttribute('data-updated')); } if(filtered.length === 0) list.innerHTML = \"<div style='text-align:center; padding:40px; color:var(--text-sec);'>Nessun elemento trovato.</div>\"; else filtered.forEach(f => list.appendChild(f)); } } ");
    html.append("function filterHomeRepos() { let val = document.getElementById('searchInput').value.toUpperCase(); let grid = document.getElementById('home-folders'); let list = document.getElementById('home-filtered-repos'); if(!val) { grid.style.display = 'grid'; list.style.display = 'none'; return; } grid.style.display = 'none'; list.style.display = 'flex'; list.innerHTML = ''; let count = 0; document.querySelectorAll('.original-card').forEach(c => { if(c.innerText.toUpperCase().includes(val)) { list.appendChild(c.cloneNode(true)); count++; } }); if(count===0) list.innerHTML = \"<div style='text-align:center; color:var(--text-sec); padding:20px;'>Nessun risultato</div>\"; } ");
    
    html.append("var rsOwner = ''; var rsRepo = ''; var rsIsPrivate = false; function openRepoSettings(owner, repo, isPrivate) { rsOwner = owner; rsRepo = repo; rsIsPrivate = (isPrivate === 'true'); document.getElementById('rs-rename-input').value = repo; document.getElementById('rs-vis-sub').innerText = rsIsPrivate ? 'Attualmente: Privata' : 'Attualmente: Pubblica'; openInternalView('view-repo-settings', 'Impostazioni Repo'); } ");
    html.append("async function executeRename() { let newName = document.getElementById('rs-rename-input').value.trim(); if(!newName || newName === rsRepo) return showToast('Nome invalido'); showToast('Rinominazione... ⏳'); try { let res = await fetch(`https://api.github.com/repos/${rsOwner}/${rsRepo}`, { method: 'PATCH', headers: { 'Authorization': 'token ' + GITHUB_TOKEN, 'Content-Type': 'application/json' }, body: JSON.stringify({ name: newName }) }); if(res.ok) { fetch(GAS_URL + '&clear_cache=true', {mode: 'no-cors'}); showToast('✅ Successo! Riavvia la Dashboard.'); setTimeout(()=>history.back(), 1500); } else showToast('❌ Errore Github'); } catch(e) { showToast('❌ Errore Rete'); } } ");
    html.append("async function executeToggleVisibility() { if(!confirm('Sicuro di voler cambiare visibilità?')) return; showToast('Modifica in corso... ⏳'); try { let res = await fetch(`https://api.github.com/repos/${rsOwner}/${rsRepo}`, { method: 'PATCH', headers: { 'Authorization': 'token ' + GITHUB_TOKEN, 'Content-Type': 'application/json' }, body: JSON.stringify({ private: !rsIsPrivate }) }); if(res.ok) { fetch(GAS_URL + '&clear_cache=true', {mode: 'no-cors'}); showToast('✅ Modificata! Riavvia.'); setTimeout(()=>history.back(), 1500); } else showToast('❌ Errore'); } catch(e) { showToast('❌ Errore Rete'); } } ");
    html.append("async function executeDeleteRepo() { let check = prompt(`Scrivi esattamente \"${rsOwner}/${rsRepo}\" per eliminare:`); if(check !== `${rsOwner}/${rsRepo}`) return showToast('Annullato.'); showToast('Eliminazione... ⏳'); try { let res = await fetch(`https://api.github.com/repos/${rsOwner}/${rsRepo}`, { method: 'DELETE', headers: { 'Authorization': 'token ' + GITHUB_TOKEN } }); if(res.ok || res.status===204) { fetch(GAS_URL + '&clear_cache=true', {mode: 'no-cors'}); showToast('✅ Eliminata! Riavvia.'); setTimeout(()=>history.back(), 1500); } else showToast('❌ Errore'); } catch(e) { showToast('❌ Errore Rete'); } } ");
    html.append("async function createRepo() { let name = document.getElementById('newRepoName').value.trim(); let desc = document.getElementById('newRepoDesc').value.trim(); let isPriv = document.getElementById('newRepoPrivate').checked; if(!name) return showToast('Inserisci un nome!'); document.getElementById('btnCreate').disabled = true; try { let res = await fetch('https://api.github.com/user/repos', { method: 'POST', headers: { 'Authorization': 'token ' + GITHUB_TOKEN, 'Content-Type': 'application/json' }, body: JSON.stringify({ name: name, description: desc, private: isPriv, auto_init: document.getElementById('newRepoReadme').checked }) }); if(res.ok) { fetch(GAS_URL + '&clear_cache=true', {mode: 'no-cors'}); showToast('✅ Creata!'); setTimeout(()=>history.back(), 1500); } } catch(e) { showToast('❌ Errore Rete'); } document.getElementById('btnCreate').disabled = false; } ");
    
    html.append("window.onload = function() { applyAppearance(); history.replaceState({path: navPath}, '', '#view-home'); if(UPDATE_JSON_URL.startsWith('http')) checkForUpdates(true); document.querySelectorAll('.original-card').forEach(c => { let name = c.getAttribute('data-name'); let isFav = appState.favorites.includes(name); if(isFav) { let btn = c.querySelector('.fav-btn'); if(btn) { btn.classList.add('active'); btn.innerText = '⭐'; } } }); window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', e => { if(appState.theme === 'system') applyAppearance(); }); renderBreadcrumbs(); }; ");
    
    /* FUNZIONE DETTAGLI REPO */
    html.append("function openRepoDetails(rName) { let r = repoDB[rName]; if(!r) return showToast('Dati mancanti'); document.getElementById('det-title').innerText = rName; let btnFav = document.getElementById('det-btn-fav'); let isFav = appState.favorites.includes(rName); btnFav.className = 'btn-icon fav-btn ' + (isFav ? 'active' : ''); btnFav.innerText = isFav ? '⭐' : '☆'; btnFav.onclick = function() { toggleFavorite(rName, this); }; let bHtml = ''; if (r.lang && r.lang !== 'N/D') bHtml += `<span class='badge-txt badge-lang'>${r.lang}</span>`; if(r.health) bHtml += `<span class='badge-txt badge-health'>${r.health}</span>`; if(r.deploy) { let bC = 'badge-health'; if(r.deploy.includes('Successo')) bC='badge-success'; else if(r.deploy.includes('Fallito')) bC='badge-danger'; else if(r.deploy.includes('esecuzione')) bC='badge-warning'; bHtml += `<span class='badge-txt ${bC}'>${r.deploy}</span>`; } if (r.isPriv === 'true') bHtml += `<span class='badge-txt badge-danger'>🔒 Privata</span>`; else bHtml += `<span class='badge-txt badge-success'>🌍 Pubblica</span>`; document.getElementById('det-badges').innerHTML = bHtml; document.getElementById('det-commit').innerText = r.commit || 'Init'; document.getElementById('det-author').innerText = (r.author ? 'Di ' + r.author + ' • ' : '') + 'Modifica: ' + r.modifica; document.getElementById('det-issues').innerText = r.issues || '0'; document.getElementById('det-metrics').innerText = r.metrics || '-'; document.getElementById('det-link-repo').onclick = function() { if(r.url) window.location.href = r.url; }; let pBtn = document.getElementById('det-link-pages'); if(r.pages && r.pages.length > 5) { pBtn.style.display = 'flex'; pBtn.onclick = function(){ window.location.href = r.pages; }; } else { pBtn.style.display = 'none'; } document.getElementById('det-link-settings').onclick = function() { openRepoSettings(r.owner, rName, r.isPriv); }; openInternalView('view-repo-details', 'Dettagli Repo'); } ");
    
    html.append("function cmpVer(a, b) { let pa = a.split('.'); let pb = b.split('.'); for(let i=0; i<3; i++) { let na = Number(pa[i]) || 0; let nb = Number(pb[i]) || 0; if(na > nb) return 1; if(nb > na) return -1; } return 0; } ");
    html.append("async function checkForUpdates(silent) { try { let sep = UPDATE_JSON_URL.includes('?') ? '&' : '?'; let res = await fetch(UPDATE_JSON_URL + sep + 't=' + Date.now()); if(res.ok) { let data = await res.json(); if(cmpVer(data.version, APP_VERSION) > 0) { newCodeUrl = data.code_url; document.getElementById('navSettingsBadge').style.display = 'block'; document.getElementById('settingsVersionBadge').style.display = 'block'; document.getElementById('aboutUpdateStatus').innerHTML = `<span style='color:var(--danger)'>Nuova ver ${data.version} disponibile!</span>`; if(silent) { document.getElementById('upd-badge-ver').innerText = 'v' + data.version; let cHtml = Array.isArray(data.changelog) ? `<ul style='margin:0;padding-left:16px;font-size:14px;line-height:1.4;'><li>${data.changelog.join('</li><li>')}</li></ul>` : data.changelog; document.getElementById('upd-changelog').innerHTML = cHtml; document.getElementById('updateModal').classList.add('active'); } } else { document.getElementById('aboutUpdateStatus').innerText = \"Sei all'ultima versione\"; } } } catch(e) {} } ");
    html.append("function skipUpdate() { document.getElementById('updateModal').classList.remove('active'); } ");
    html.append("async function downloadUpdate() { showToast('Download in corso... ⏳'); try { let sep = newCodeUrl.includes('?') ? '&' : '?'; let res = await fetch(newCodeUrl + sep + 't=' + Date.now()); if(res.ok) { let code = await res.text(); var ta = document.createElement('textarea'); ta.value = code; document.body.appendChild(ta); ta.select(); document.execCommand('copy'); document.body.removeChild(ta); skipUpdate(); showToast('✅ Codice copiato! Incollalo in Tasker.'); } } catch(e) { showToast('❌ Errore'); } } ");
    html.append("async function loadProfileAPI() { showToast('Caricamento...'); try { let res = await fetch('https://api.github.com/user', {headers:{'Authorization':'token '+GITHUB_TOKEN}}); let d = await res.json(); document.getElementById('content-profile').innerHTML = `<div class='container' style='text-align:center;'><img src='${d.avatar_url}' class='profile-img'><h2>${d.name||d.login}</h2><p style='color:var(--text-sec)'>${d.bio||''}</p><div class='dashboard-stats' style='margin-top:24px;'><div class='stat-box'><div class='stat-title'>Followers</div><div class='stat-num'>${d.followers}</div></div><div class='stat-box'><div class='stat-title'>Following</div><div class='stat-num'>${d.following}</div></div></div></div>`; openInternalView('view-profile', 'Profilo'); } catch(e) {} } ");
    html.append("function loadNotifications() { document.getElementById('content-notifications').innerHTML = \"<div class='container' style='text-align:center;padding:40px;color:var(--text-sec)'>🎉 Nessuna notifica</div>\"; } ");
    html.append("async function loadExplore() { let container = document.getElementById('content-explore'); container.innerHTML = \"<div class='loader' style='margin:40px auto;'></div>\"; try { let res = await fetch('https://api.github.com/search/repositories?q=stars:>50000&sort=stars&order=desc&per_page=15', {headers:{'Authorization':'token '+GITHUB_TOKEN}}); let data = await res.json(); let h = \"<div class='repo-container'>\"; data.items.forEach(r => { h += `<div class='repo-card'><div class='repo-header'><div class='repo-name'>${r.full_name}</div></div><div class='repo-meta' style='margin-bottom:8px'>${r.description || ''}</div><div class='badges'><span class='badge-txt badge-warning'>⭐ ${r.stargazers_count}</span><span class='badge-txt badge-lang'>${r.language || 'N/D'}</span></div></div>`; }); h += \"</div>\"; container.innerHTML = h; } catch(e) { container.innerHTML = \"<div style='text-align:center;padding:20px'>Errore Rete</div>\"; } } ");

    html.append("</script></body></html>");
}

activity.runOnUiThread(new Runnable() {
    run() { webView.loadDataWithBaseURL("https://app.githubmanager/", html.toString(), "text/html", "UTF-8", null); }
});