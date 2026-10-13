package cl.bonvallarte.multitraductor.audio
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioLevelMeterTest {
    @Test fun silence() = assertEquals(-90.0, AudioLevelMeter.dbfs(shortArrayOf(0,0),2),0.001)
    @Test fun halfScale() = assertEquals(-6.0206, AudioLevelMeter.dbfs(shortArrayOf(16384,-16384),2),0.03)
    @Test fun empty() = assertEquals(-90.0, AudioLevelMeter.dbfs(shortArrayOf(),0),0.001)
}
