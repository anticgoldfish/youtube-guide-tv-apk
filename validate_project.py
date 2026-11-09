"""Fast offline checks for the fork overlay and workflow. Not a substitute for Android compilation."""
from pathlib import Path
import re
import unittest
root=Path(__file__).resolve().parent
java=(root/'fork-overlay/src/com/youtubeguide/tv/GuideActivity.java').read_text()
repo=(root/'fork-overlay/src/com/youtubeguide/tv/GuideRepository.java').read_text()
player=(root/'apply_overlay.py').read_text()
flow=(root/'.github/workflows/build-smarttube-guide-apk.yml').read_text()
class Checks(unittest.TestCase):
 def test_read_only_public_data(self):
  self.assertIn('raw.githubusercontent.com/anticgoldfish/youtube-guide-surfer/main/YouTube-Guide/channels.json',repo)
  self.assertIn('github.io/youtube-guide-surfer/channels.json',repo)
  self.assertNotIn('github_pat_',repo)
  self.assertNotIn('AIza',repo)
 def test_auto_sync_and_cache(self):
  self.assertIn('300000L',repo)
  self.assertIn('getSharedPreferences("guide_cache"',repo)
  self.assertIn('putString("json",txt)',repo)
 def test_tv_live_and_debug(self):
  self.assertIn('EmbedPlayerView',java)
  self.assertIn('setMute(true)',java)
  self.assertIn('PlaybackPresenter.instance(this).openVideo(p.id,false',java)
  self.assertEqual(java.count('KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_UP'),1)
  self.assertIn('repo.refresh(true',java)
 def test_no_channel_creation(self):
  self.assertNotIn('Generate & Save Channel',java)
  self.assertNotIn('YOUTUBE_API_KEY',java)
 def test_fork_not_external_player(self):
  self.assertIn('setRoot(com.youtubeguide.tv.GuideActivity.class)',player)
  self.assertIn('PlaybackView.class, PlaybackActivity.class, com.youtubeguide.tv.GuideActivity.class',player)
  self.assertIn('showSuggestions(boolean show)',player)
  self.assertNotIn('ACTION_VIEW',java)
 def test_builder(self):
  self.assertIn('git clone --recursive',flow)
  self.assertIn('2bcad2c130f5789af22094a59c029d14461825f0',flow)
  self.assertIn('assembleStstableDebug',flow)
  self.assertIn('upload-artifact@v4',flow)
if __name__=='__main__':unittest.main(verbosity=2)
