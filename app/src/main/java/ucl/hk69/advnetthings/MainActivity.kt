package ucl.hk69.advnetthings

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.os.Bundle
import android.util.Base64
import android.util.Log
import com.google.android.things.pio.Gpio
import com.google.android.things.pio.PeripheralManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import javax.crypto.interfaces.DHPrivateKey
import javax.crypto.interfaces.DHPublicKey
import javax.crypto.spec.IvParameterSpec

class MainActivity : Activity() {
    private val mySup = MySupportClass()
    val ip = "192.168.1.25"
    var svSoc: ServerSocket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d("setup", "GPIO周りの準備")

        val manager = PeripheralManager.getInstance()
        val ledKaigi = setLed("BCM4", manager)
        val ledRed = setLed("BCM17", manager)
        val ledYellow = setLed("BCM27", manager)
        val ledGreen = setLed("BCM22", manager)

        Log.d("setup", "GPIO周りの準備完了")

        GlobalScope.launch {
            val svBtSoc = BluetoothAdapter.getDefaultAdapter()
                .listenUsingRfcommWithServiceRecord("test", mySup.MY_UUID)
            Log.d("bluetooth", "svBtSock作成&受信待機")
            val btSoc = svBtSoc.accept()
            Log.d("bluetooth", "受信")
            val btDos = DataOutputStream(btSoc.outputStream)
            val btDis = DataInputStream(btSoc.inputStream)

            val p = btDis.readUTF().toBigInteger()
            val g = btDis.readUTF().toBigInteger()
            val othersY = btDis.readUTF().toBigInteger()
            Log.d("dh", "$p \n $g \n $othersY")

            val keyPair = mySup.makeKeyPair(p, g)
            val privKey = keyPair.private as DHPrivateKey
            val pubKey = keyPair.public as DHPublicKey
            val y = pubKey.y
            Log.d("dh", "送信：$y")
            btDos.writeUTF(y.toString())

            val secKey = mySup.genSecKey(p, g, othersY, privKey)
            Log.d("make secKey", "鍵生成完了")

            svSoc = ServerSocket()
            svSoc!!.bind(InetSocketAddress(ip, mySup.PORT))
            Log.d("debug", "サーバソケット作成")

            if (btSoc != null) {
                try {
                    btDis.close()
                    btDos.close()
                    btSoc.close()
                    svBtSoc.close()
                }catch (e:Exception){
                    e.printStackTrace()
                }
            }

            while (true) {
                Log.d("debug", "受信待機")
                var soc: Socket? = svSoc!!.accept()
                val dis = DataInputStream(soc!!.getInputStream())
                val dos = DataOutputStream(soc.getOutputStream())
                Log.d("debug", "DIS作成 $dis")

                while (soc != null) {
                    try {
                        val iv = Base64.decode(dis.readUTF(), Base64.DEFAULT)
                        val ivParamSpec = IvParameterSpec(iv)
                        val msg = mySup.dec(Base64.decode(dis.readUTF(), Base64.DEFAULT), secKey, ivParamSpec)
                        Log.d("receive message", "$msg")

                        when (msg) {
                            mySup.KAIGI_ON -> ledKaigi.value = true
                            mySup.KAIGI_OFF -> ledKaigi.value = false
                            mySup.STATE_RED -> {
                                ledRed.value = true
                                ledYellow.value = false
                                ledGreen.value = false
                            }
                            mySup.STATE_YELLOW -> {
                                ledRed.value = false
                                ledYellow.value = true
                                ledGreen.value = false
                            }
                            mySup.STATE_GREEN -> {
                                ledRed.value = false
                                ledYellow.value = false
                                ledGreen.value = true
                            }
                            mySup.STATE_OFF ->{
                                ledRed.value = false
                                ledYellow.value = false
                                ledGreen.value = false
                            }
                        }

                    }catch (e:Exception){
                        e.printStackTrace()
                        soc = null
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        svSoc?.close()
        super.onDestroy()
    }

    private fun setLed(pin:String, manager:PeripheralManager):Gpio{
        val led = manager.openGpio(pin)
        led.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
        led.setActiveType(Gpio.ACTIVE_HIGH)

        return led
    }
}