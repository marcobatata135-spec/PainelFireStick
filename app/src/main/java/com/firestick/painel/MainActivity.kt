package com.firestick.painel

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private var dadb: Dadb? = null
    private lateinit var ip: EditText
    private lateinit var status: TextView
    private lateinit var help: TextView
    private lateinit var go: Button
    private val keys = mutableListOf<Button>()
    private var d = 1f

    private fun dp(v: Int) = (v * d).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        d = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(16))
        }
        ip = EditText(this).apply {
            hint = "Endereço do Fire Stick"
            setText(getPreferences(MODE_PRIVATE).getString("ip", "192.168.0.113"))
            setSingleLine()
        }
        go = Button(this).apply { text = "Conectar"; setOnClickListener { connect() } }
        status = TextView(this).apply { text = "Desconectado"; textSize = 16f; setPadding(0, dp(8), 0, 0) }
        help = TextView(this).apply { setTextColor(Color.rgb(180, 64, 14)); setPadding(0, dp(4), 0, dp(8)); setTextIsSelectable(true) }
        root.addView(ip); root.addView(go); root.addView(status); root.addView(help)

        root.addView(row(spacer(), key("▲", "input keyevent 19"), spacer()))
        root.addView(row(key("◀", "input keyevent 21"), key("OK", "input keyevent 23"), key("▶", "input keyevent 22")))
        root.addView(row(spacer(), key("▼", "input keyevent 20"), spacer()))
        root.addView(row(key("Voltar", "input keyevent 4"), key("Início", "input keyevent 3"), key("Menu", "input keyevent 82")))
        root.addView(row(key("Vol -", "input keyevent 25"), key("Mudo", "input keyevent 164"), key("Vol +", "input keyevent 24")))
        root.addView(row(key("YouTube", app("com.amazon.firetv.youtube")), key("Netflix", app("com.netflix.ninja"))))
        root.addView(row(key("Prime Video", app("com.amazon.avod")), key("Configurações", "am start -a android.settings.SETTINGS")))
        setContentView(ScrollView(this).apply { addView(root) })
        setConnected(false)
    }

    private fun app(pkg: String) = "monkey -p $pkg -c android.intent.category.LAUNCHER 1"

    private fun spacer() = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, dp(56), 1f) }

    private fun row(vararg v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        v.forEach { addView(it) }
    }

    private fun key(label: String, cmd: String) = Button(this).apply {
        text = label
        isAllCaps = false
        layoutParams = LinearLayout.LayoutParams(0, dp(56), 1f)
        setOnClickListener { send(cmd) }
        keys.add(this)
    }

    private fun setConnected(on: Boolean) = keys.forEach { it.isEnabled = on }

    private fun ui(f: () -> Unit) = runOnUiThread(f)

    private fun send(cmd: String) {
        io.execute {
            try { dadb?.shell(cmd) } catch (e: Throwable) {
                ui { status.text = "Conexão perdida"; help.text = "Toque em Conectar para tentar de novo."; setConnected(false); go.text = "Conectar" }
            }
        }
    }

    private fun connect() {
        val host = ip.text.toString().trim()
        if (host.isEmpty()) return
        go.isEnabled = false; go.text = "Conectando..."; status.text = "Conectando..."; help.text = ""
        io.execute {
            var step = "início"
            try {
                try { dadb?.close() } catch (_: Throwable) {}
                dadb = null
                step = "1/4 procurando o Fire Stick na porta 5555"
                ui { status.text = "Passo 1/4: procurando o Fire Stick..." }
                Socket().use { it.connect(InetSocketAddress(host, 5555), 5000) }
                step = "2/4 criando a chave de segurança"
                ui { status.text = "Passo 2/4: preparando a chave..." }
                val priv = File(filesDir, "adbkey")
                val pub = File(filesDir, "adbkey.pub")
                if (!priv.exists()) AdbKeyPair.generate(priv, pub)
                val keyPair = AdbKeyPair.read(priv, pub)
                step = "3/4 autenticando no Fire Stick (olhe a TV e aceite)"
                ui { status.text = "Passo 3/4: olhe a TV e aceite a autorização (Sempre permitir > OK)." }
                val dd = Dadb.create(host, 5555, keyPair)
                step = "4/4 enviando comando de teste"
                ui { status.text = "Passo 4/4: testando..." }
                dd.shell("echo ok")
                dadb = dd
                getPreferences(MODE_PRIVATE).edit().putString("ip", host).apply()
                ui { status.text = "Conectado"; setConnected(true); go.text = "Reconectar"; go.isEnabled = true }
            } catch (e: Throwable) {
                val causa = e.cause?.let { " | causa: " + it.javaClass.simpleName + ": " + it.message } ?: ""
                fail("Falhou no passo " + step + ".\nErro: " + e.javaClass.simpleName + ": " + (e.message ?: "sem mensagem") + causa)
            }
        }
    }

    private fun fail(msg: String) = ui {
        status.text = "Não foi possível conectar"; help.text = msg
        go.text = "Tentar de novo"; go.isEnabled = true; setConnected(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { dadb?.close() } catch (_: Throwable) {}
        io.shutdown()
    }
}
