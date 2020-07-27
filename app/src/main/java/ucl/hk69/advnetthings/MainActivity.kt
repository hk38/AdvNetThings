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
import java.net.*
import java.util.*
import javax.crypto.interfaces.DHPrivateKey
import javax.crypto.interfaces.DHPublicKey
import javax.crypto.spec.IvParameterSpec

class MainActivity : Activity() {
    private val mySup = MySupportClass()
    var svSoc: ServerSocket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val manager = PeripheralManager.getInstance()
        val ledReceive = setLed("BCM12", manager)
        val ledKaigi = setLed("BCM4", manager)
        val ledRed = setLed("BCM17", manager)
        val ledYellow = setLed("BCM27", manager)
        val ledGreen = setLed("BCM22", manager)

        val ip = getLocalAddress()

        if(ip == null){
            Log.d("atMsg", "error")
            return
        }

        GlobalScope.launch {
            val svBtSoc = BluetoothAdapter.getDefaultAdapter()
                .listenUsingRfcommWithServiceRecord("test", mySup.MY_UUID)
            val btSoc = svBtSoc.accept()
            val btDos = DataOutputStream(btSoc.outputStream)
            val btDis = DataInputStream(btSoc.inputStream)

            val p = btDis.readUTF().toBigInteger()
            val g = btDis.readUTF().toBigInteger()
            val othersY = btDis.readUTF().toBigInteger()

            val keyPair = mySup.makeKeyPair(p, g)
            val privKey = keyPair.private as DHPrivateKey
            val pubKey = keyPair.public as DHPublicKey
            val y = pubKey.y
            btDos.writeUTF(y.toString())
            btDos.writeUTF(ip!!.hostAddress)
            btDos.flush()
            val secKey = mySup.genSecKey(p, g, othersY, privKey)

            svSoc = ServerSocket()
            svSoc!!.bind(InetSocketAddress(ip, mySup.PORT))

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
                ledReceive.value = true
                var soc: Socket? = svSoc!!.accept()
                val dis = DataInputStream(soc!!.getInputStream())
                ledReceive.value = false
                var secNo = 0
                while (soc != null) {
                    try {
                        val iv = Base64.decode(dis.readUTF(), Base64.DEFAULT)
                        val ivParamSpec = IvParameterSpec(iv)
                        val msg = mySup.dec(dis.readUTF(), secKey, ivParamSpec)

                        if(secNo+1 != msg/10) break

                        secNo++
                        when (msg%10) {
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

    private fun getLocalAddress(): InetAddress? {
        val ifaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
        while (ifaces.hasMoreElements()) {
            val iface: NetworkInterface = ifaces.nextElement()
            val addresses: Enumeration<InetAddress> = iface.inetAddresses
            while (addresses.hasMoreElements()) {
                val addr: InetAddress = addresses.nextElement()
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    return addr
                }
            }
        }
        return null
    }
}