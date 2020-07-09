package ucl.hk69.advnetthings

import android.bluetooth.BluetoothAdapter
import android.content.SharedPreferences
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.FileUtils
import android.widget.Toast
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.android.things.pio.Gpio
import com.google.android.things.pio.PeripheralManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.lang.Exception
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import javax.crypto.interfaces.DHPrivateKey
import javax.crypto.interfaces.DHPublicKey

class MainActivity : AppCompatActivity() {
    private val mySup = MySupportClass()
    val ip = "192.168.1.100"
    var svSoc: ServerSocket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val manager = PeripheralManager.getInstance()
        val ledKaigi = setLed("BCM4", manager)
        val ledRed = setLed("BCM17", manager)
        val ledYellow = setLed("BCM27", manager)
        val ledGreen = setLed("BCM22", manager)

        val keyPair = mySup.genKeyPair()
        val pubKey = keyPair.public as DHPublicKey
        val paramSpec = pubKey.params
        val p = paramSpec.p
        val g = paramSpec.g

        val privKey = keyPair.private as DHPrivateKey
        val y = pubKey.y

        Toast.makeText(this, "genDHKey", Toast.LENGTH_SHORT).show()

        GlobalScope.launch {
            val svBtSoc = BluetoothAdapter.getDefaultAdapter()
                .listenUsingRfcommWithServiceRecord("test", mySup.MY_UUID)
            val btSoc = svBtSoc.accept()
            val btDos = DataOutputStream(btSoc.outputStream)
            val btDis = DataInputStream(btSoc.inputStream)

            btDos.writeUTF(p.toString())
            btDos.writeUTF(g.toString())
            btDos.writeUTF(y.toString())
            val othersY = btDis.readUTF().toBigInteger()

            val secKey = mySup.genSecKey(p, g, othersY, privKey)

            svSoc = ServerSocket()
            svSoc!!.bind(InetSocketAddress(ip, mySup.PORT))

            if (btSoc.isConnected) {
                btDis.close()
                btDos.close()
                btSoc.close()
                svBtSoc.close()
            }

            while (true) {
                val soc = svSoc!!.accept()
                val dis = DataInputStream(soc.getInputStream())

                while (soc.isConnected) {
                    when (dis.readInt()) {
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
                        mySup.DISCONNECT ->{
                            if(soc.isConnected) {
                                dis.close()
                                soc.close()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        svSoc?.close()
        super.onDestroy()
    }

    fun setLed(pin:String, manager:PeripheralManager):Gpio{
        val led = manager.openGpio(pin)
        led.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
        led.setActiveType(Gpio.ACTIVE_HIGH)

        return led
    }
}