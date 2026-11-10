"""Exercise the actual offline inspector with minimal synthetic policy declarations."""
import unittest
import json
from pathlib import Path
import xml.etree.ElementTree as ET
from inspect_audio_policy import inspect, declared_format, native_comparison


class PolicyTests(unittest.TestCase):
    def test_profiles_must_not_be_combined_into_invented_native_format(self):
        port = {'profiles': [
            {'format': 'AUDIO_FORMAT_PCM_16_BIT', 'samplingRates': '48000',
             'channelMasks': 'AUDIO_CHANNEL_OUT_STEREO'},
            {'format': 'AUDIO_FORMAT_PCM_16_BIT', 'samplingRates': '16000',
             'channelMasks': 'AUDIO_CHANNEL_OUT_MONO'}]}
        self.assertEqual(declared_format(port, 48000, 'AUDIO_CHANNEL_OUT_MONO'), 'NOT_DECLARED')
        self.assertEqual(declared_format(port, 48000, 'AUDIO_CHANNEL_OUT_STEREO'), 'EXACT_DECLARATION')

    def test_missing_or_dynamic_profiles_are_unknown(self):
        for port in ({}, {'profiles': [{}]}, {'profiles': [{'samplingRates': 'dynamic'}]}):
            self.assertEqual(declared_format(port, 48000, 'AUDIO_CHANNEL_OUT_STEREO'), 'UNKNOWN')
        port = {'profiles': [{'samplingRates': 'dynamic', 'channelMasks': 'AUDIO_CHANNEL_OUT_MONO'}]}
        self.assertEqual(declared_format(port, 48000, 'AUDIO_CHANNEL_OUT_STEREO'), 'NOT_DECLARED')

    def test_published_mp01_evidence_preserves_variant_difference(self):
        evidence = json.loads((Path(__file__).resolve().parent.parent /
                              'docs/research/MP01_POLICY_SUMMARY.json').read_text())
        platform, common = [native_comparison(p['analysis']['modules'])['routes']
                            for p in evidence['policies']]
        reviewed = json.loads((Path(__file__).resolve().parent.parent /
                               'docs/research/MP01_NATIVE_FORMAT_REVIEW.json').read_text())
        for original, current in zip(evidence['policies'], reviewed['policies'], strict=True):
            self.assertEqual(original['source'], current['source'])
            self.assertEqual(original['sha256'], current['sha256'])
            self.assertEqual(native_comparison(original['analysis']['modules']),
                             current['native_format_comparison'])
        uplink = [row for row in platform if row['direction'] == 'UPLINK']
        self.assertEqual(len(uplink), 1)
        self.assertEqual(uplink[0]['mix'], 'incall_music_uplink')
        self.assertEqual(uplink[0]['pcm16_16000_mono'], 'NOT_DECLARED')
        self.assertEqual(uplink[0]['pcm16_48000_stereo'], 'EXACT_DECLARATION')
        self.assertFalse(any(row['direction'] == 'UPLINK' for row in common))
        self.assertTrue(any(row['direction'] == 'DOWNLINK' and
                            row['pcm16_16000_mono'] == 'EXACT_DECLARATION' for row in platform))

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
