package org.softeg.slartus.forpdaplus.classes;

import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.view.KeyEvent;
import android.view.Window;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.softeg.slartus.forpdacommon.Connectivity;
import org.softeg.slartus.forpdacommon.ExtPreferences;
import org.softeg.slartus.forpdaplus.App;
import org.softeg.slartus.forpdaplus.fragments.topic.ThemeFragment;

/**
 * Created by IntelliJ IDEA.
 * User: slinkin
 * Date: 24.10.12
 * Time: 8:31
 * To change this template use File | Settings | File Templates.
 */
public class WebViewExternals {

    private IWebViewContainer m_WebViewContainer;


    public WebViewExternals(IWebViewContainer webViewContainer) {

        m_WebViewContainer = webViewContainer;
    }

    protected String Prefix() {
        return m_WebViewContainer.Prefix();
    }

    protected WebView getWebView() {
        return m_WebViewContainer.getWebView();
    }

    private Boolean m_UseVolumesScroll = false;


    private Boolean m_LoadsImagesAutomatically = true;
    private Boolean m_KeepScreenOn = false;



    private Boolean m_CurrentFullScreen = false;

    protected Boolean getCurrentFullScreen() {
        return m_CurrentFullScreen;
    }

    private Window getWindow() {
        return m_WebViewContainer.getWindow();
    }

    private ActionBar getSupportActionBar() {
        return m_WebViewContainer.getSupportActionBar();
    }

    public void onPrepareOptionsMenu() {


    }


    public void setWebViewSettings() {
        setWebViewSettings(false);
    }

    public void setWebViewSettings(Boolean loadImagesAutomaticallyAlways) {
        WebView webView = getWebView();
        disableWebViewCache();
        webView.setBackgroundColor(App.getInstance().getThemeStyleWebViewBackground());
        webView.loadData("<html><head></head><body bgcolor=" + App.getInstance().getCurrentBackgroundColorHtml() + "></body></html>", "text/html", "UTF-8");


        webView.getSettings().setLoadsImagesAutomatically(loadImagesAutomaticallyAlways || m_LoadsImagesAutomatically);
        webView.setKeepScreenOn(m_KeepScreenOn);
        webView.getSettings().setBuiltInZoomControls(false);

        if(Build.VERSION.SDK_INT<18)
        webView.getSettings().setPluginState(WebSettings.PluginState.ON);
    }

    private void disableWebViewCache() {
        getWebView().getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
    }

    public void loadPreferences(SharedPreferences prefs) {
        String prefix = Prefix();


        m_UseVolumesScroll = prefs.getBoolean(prefix + ".UseVolumesScroll", false);

        m_LoadsImagesAutomatically = isLoadImages(prefs, prefix);

        m_KeepScreenOn = prefs.getBoolean(prefix + ".KeepScreenOn", false);
    }

    public static Boolean isLoadImages(String prefix) {
        if (ThemeFragment.LoadsImagesAutomatically != null)
            return ThemeFragment.LoadsImagesAutomatically;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(App.getInstance());
        return isLoadImages(prefs, prefix);
    }

    public static Boolean isLoadImages(SharedPreferences prefs, String prefix) {
        int loadImagesType = ExtPreferences.parseInt(prefs, prefix + ".LoadsImages", 1);
        if (loadImagesType == 2) {
            return Connectivity.isConnectedWifi(App.getContext());
        }

        return loadImagesType == 1;
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        return scrollByKeys(event) || pageNavigationsByKeys(event) || m_WebViewContainer.dispatchSuperKeyEvent(event);

    }

    private boolean scrollByKeys(KeyEvent event) {


        WebView scrollView = getWebView();

        if (m_UseVolumesScroll) {
            int action = event.getAction();

            String scrollUpKeys = "," + PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getString("keys.scrollUp", "24").replace(" ", "") + ",";
            String scrollDownKeys = "," + PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getString("keys.scrollDown", "25").replace(" ", "") + ",";

            int keyCode = event.getKeyCode();

            if (scrollUpKeys.contains("," + Integer.toString(keyCode) + ",")) {
                if (action == KeyEvent.ACTION_DOWN)
                    scrollView.pageUp(false);
                return true;// true надо обязательно возвращать даже если не ACTION_DOWN иначе звук нажатия
            } else if (scrollDownKeys.contains("," + Integer.toString(keyCode) + ",")) {
                if (action == KeyEvent.ACTION_DOWN)
                    scrollView.pageDown(false);
                return true;// true надо обязательно возвращать даже если не ACTION_DOWN иначе звук нажатия
            }

        }

        return false;
    }

    /**
     * Обработка клавиатуры для nook simple touch
     *
     */
    private boolean pageNavigationsByKeys(KeyEvent event) {
        int action = event.getAction();
        if (action == KeyEvent.ACTION_DOWN) {
            String prevPageKeys = "," + PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getString("keys.prevPage", "158").replace(" ", "") + ",";
            String nextPageKeys = "," + PreferenceManager.getDefaultSharedPreferences(App.getContext())
                    .getString("keys.nextPage", "407").replace(" ", "") + ",";

            int keyCode = event.getKeyCode();

            if (prevPageKeys.contains("," + Integer.toString(keyCode) + ",")) {
                m_WebViewContainer.prevPage();
                return true;
            } else if (nextPageKeys.contains("," + Integer.toString(keyCode) + ",")) {
                m_WebViewContainer.nextPage();
                return true;
            }
        }

        return false;
    }

}
