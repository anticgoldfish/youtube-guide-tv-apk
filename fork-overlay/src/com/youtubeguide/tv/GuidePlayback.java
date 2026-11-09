package com.youtubeguide.tv;

/** Process-local guide playback state, shared with patched SmartTube PlaybackActivity. */
public final class GuidePlayback {
    public static volatile GuideRepository repo;
    public static volatile String activeChannel;
    public static volatile String activeVideo;
    private GuidePlayback(){}
    public static GuideRepository.Program current(){
        GuideRepository r=repo;
        if(r==null||activeChannel==null)return null;
        for(GuideRepository.Channel c:r.channels())if(activeChannel.equals(c.id))return r.at(c,System.currentTimeMillis());
        return null;
    }
}
