"""Exercise the actual offline inspector with minimal synthetic policy declarations."""
import unittest
import xml.etree.ElementTree as ET
from inspect_audio_policy import inspect


class PolicyTests(unittest.TestCase):
    def test_empty_is_not_support(self):
        result = inspect(ET.fromstring('<audioPolicyConfiguration/>'))
        self.assertEqual(result['modules'], [])
        self.assertEqual(result['evidence'], 'DECLARED_POLICY_ONLY_NOT_AUDIBILITY')

    def test_voice_call_alias_normalizes_and_preserves_native_formats(self):
        result = inspect(ET.fromstring('''<audioPolicyConfiguration><modules><module name="primary">
          <mixPorts><mixPort name="capture" role="sink"><profile samplingRates="16000"/></mixPort>
          <mixPort name="incall" role="source" flags="AUDIO_OUTPUT_FLAG_INCALL_MUSIC">
            <profile samplingRates="48000" channelMasks="AUDIO_CHANNEL_OUT_STEREO"/>
          </mixPort></mixPorts><devicePorts>
            <devicePort tagName="Voice Call In" type="AUDIO_DEVICE_IN_VOICE_CALL"/>
            <devicePort tagName="Telephony Tx" type="AUDIO_DEVICE_OUT_TELEPHONY_TX"/>
          </devicePorts><routes>
            <route sink="capture" sources="Voice Call In"/>
            <route sink="Telephony Tx" sources="mic, incall"/>
          </routes></module></modules></audioPolicyConfiguration>'''))['modules'][0]
        self.assertEqual(result['call_input_devices'][0]['type'], 'AUDIO_DEVICE_IN_VOICE_CALL')
        self.assertEqual(result['call_input_devices'][0]['canonical_type'], 'AUDIO_DEVICE_IN_TELEPHONY_RX')
        self.assertEqual(result['downlink_routes'][0]['capture_mix']['name'], 'capture')
        playback = result['uplink_routes'][0]['playback_mixes'][0]
        self.assertEqual(playback['profiles'][0]['samplingRates'], '48000')
        self.assertEqual(playback['profiles'][0]['channelMasks'], 'AUDIO_CHANNEL_OUT_STEREO')

    def test_telephony_sink_without_playback_mix_does_not_invent_one(self):
        result = inspect(ET.fromstring('''<audioPolicyConfiguration><modules><module name="primary">
          <devicePorts><devicePort tagName="Tx" type="AUDIO_DEVICE_OUT_TELEPHONY_TX"/></devicePorts>
          <routes><route sink="Tx" sources="mic"/></routes>
          </module></modules></audioPolicyConfiguration>'''))
        self.assertEqual(result['modules'][0]['uplink_routes'][0]['playback_mixes'], [])

    def test_includes_are_reported_and_never_fetched(self):
        result = inspect(ET.fromstring('''<audioPolicyConfiguration xmlns:xi="http://www.w3.org/2001/XInclude">
          <xi:include href="missing.xml"/></audioPolicyConfiguration>'''))
        self.assertEqual(result['unresolved_includes'], ['missing.xml'])


if __name__ == '__main__':
    unittest.main()
