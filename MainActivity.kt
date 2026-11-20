package com.higa.resultados

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var status: TextView

    private val higaUrl =
        "http://higamdp.ddns.net:8070/intranet/higa/index.php?action=laboratorio&subaction=consulta-resultados"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        status = findViewById(R.id.statusText)

        val dniEdit: EditText = findViewById(R.id.dniEdit)
        val fromEdit: EditText = findViewById(R.id.fromEdit)
        val toEdit: EditText = findViewById(R.id.toEdit)
        val prepare: Button = findViewById(R.id.prepareButton)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.setSupportZoom(true)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (url.contains("higamdp.ddns.net")) {
                    injectAutomation(view)
                    status.text = "HIGA cargado. Escribí el DNI y tocá «Seleccionar D.N.I. y completar»."
                }
            }
        }

        prepare.setOnClickListener {
            val dni = dniEdit.text.toString().trim()
            val from = fromEdit.text.toString().trim()
            val to = toEdit.text.toString().trim()

            if (dni.isEmpty()) {
                status.text = "Falta el DNI."
                return@setOnClickListener
            }

            val safeDni = jsQuote(dni)
            val safeFrom = jsQuote(from)
            val safeTo = jsQuote(to)

            val script = """
                (function(){
                  if (!window.higaSetDni) { return JSON.stringify({ok:false,error:'La página todavía no terminó de cargar'}); }
                  var a = window.higaSetDni($safeDni);
                  var b = window.higaSetDates($safeFrom, $safeTo);
                  return JSON.stringify({ok:true,dni:a,dates:b});
                })();
            """.trimIndent()

            webView.evaluateJavascript(script) { result ->
                status.text = "Preparación enviada. Revisá que D.N.I. quede seleccionado y que el DNI aparezca en el campo."
            }
        }

        webView.loadUrl(higaUrl)
    }

    private fun jsQuote(value: String): String =
        "'" + value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n") + "'"

    private fun injectAutomation(view: WebView) {
        val js = """
            (function() {
              if (window.__higaAutomationInstalled) return;
              window.__higaAutomationInstalled = true;

              function visible(el) {
                if (!el) return false;
                const r = el.getBoundingClientRect();
                return r.width > 0 && r.height > 0;
              }
              function norm(s) {
                return (s || '').replace(/\s+/g,' ').trim().toLowerCase();
              }
              function fire(el) {
                el.dispatchEvent(new Event('input',{bubbles:true}));
                el.dispatchEvent(new Event('change',{bubbles:true}));
                el.dispatchEvent(new Event('blur',{bubbles:true}));
              }
              function chooseDni() {
                const selects = Array.from(document.querySelectorAll('select')).filter(visible);
                for (const sel of selects) {
                  const opt = Array.from(sel.options).find(o => {
                    const t = norm(o.textContent);
                    return t === 'd.n.i.' || t === 'dni' || t.includes('d.n.i.');
                  });
                  if (opt) {
                    sel.value = opt.value;
                    fire(sel);
                    return true;
                  }
                }
                return false;
              }
              function allInputs() {
                return Array.from(document.querySelectorAll('input')).filter(visible)
                  .filter(i => !['hidden','submit','button'].includes((i.type||'text')));
              }
              window.higaSetDni = function(dni) {
                const selected = chooseDni();
                const fields = allInputs();
                let el = fields.find(i => /dni|documento|document/i.test(
                  (i.name||'')+' '+(i.id||'')+' '+(i.placeholder||'')
                ));
                if (!el) el = fields[0];
                if (el) {
                  el.focus();
                  el.value = dni;
                  fire(el);
                }
                return {dniSelected:selected, dniFilled:!!el};
              };
              function setDateValue(el, value) {
                if (!el) return;
                let v = value;
                if (el.type === 'date' && /^\d{2}\/\d{2}\/\d{4}$/.test(value)) {
                  const p=value.split('/');
                  v=p[2]+'-'+p[1]+'-'+p[0];
                }
                el.value=v;
                fire(el);
              }
              window.higaSetDates = function(from,to) {
                const fields = allInputs();
                const dates = fields.filter(i => i.type === 'date' || /fecha|date/i.test(
                  (i.name||'')+' '+(i.id||'')+' '+(i.placeholder||'')
                ));
                if (dates[0] && from) setDateValue(dates[0],from);
                if (dates[1] && to) setDateValue(dates[1],to);
                return dates.length;
              };
            })();
        """.trimIndent()
        view.evaluateJavascript(js, null)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
