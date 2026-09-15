package com.kemi.dualscreensaver;
import java.util.Random;
/** One immutable random content choice per session, with a shared looping frame timeline. */
public final class PlaybackSequence {
    private final Random random;
    private final int[] choices;
    private boolean active;
    private int current;
    private long session,loop,frame,duration,timelineBase,firstPts=Long.MIN_VALUE,lastPts=Long.MIN_VALUE,lastTimeline;
    public PlaybackSequence(Random random) { this(random,new int[]{1,2,3}); }
    public PlaybackSequence(Random random,int[] choices) {
        if(choices.length==0) throw new IllegalArgumentException("Empty pool");
        this.random=random;this.choices=choices.clone();
    }
    public void start() { start(session+1); }
    public void start(long id) {
        if(id<=session) throw new IllegalArgumentException("session must increase");
        session=id;current=choices[random.nextInt(choices.length)];active=true;
        loop=frame=duration=timelineBase=lastTimeline=0;firstPts=lastPts=Long.MIN_VALUE;
    }
    public void startSelected(long id,int selected) {
        boolean valid=false;for(int value:choices) if(value==selected) valid=true;
        if(!valid || id<=session) throw new IllegalArgumentException("selection/session");
        session=id;current=selected;active=true;
        loop=frame=duration=timelineBase=lastTimeline=0;firstPts=lastPts=Long.MIN_VALUE;
    }
    public void duration(long ns) { if(ns<=0) throw new IllegalArgumentException("duration");duration=ns; }
    public void stop() { active=false; }
    public boolean active() { return active; }
    public int current() { if(!active) throw new IllegalStateException("No session");return current; }
    public long session() { return session; }
    public long frame() { return frame; }
    public boolean matches(long expectedSession) { return active && session==expectedSession; }
    public Frame acquired(long pts) {
        if(!active || pts==lastPts) return null;
        if(firstPts==Long.MIN_VALUE) firstPts=pts;
        if(lastPts!=Long.MIN_VALUE && pts<lastPts) {
            // Native looping may reset source PTS; reject small backwards/stale buffers.
            if(duration==0 || lastPts-pts<duration/2) return null;
            timelineBase=Math.max((loop+1)*duration,lastTimeline+41_666_667L);
            firstPts=pts;
        }
        long timeline=timelineBase+Math.max(0,pts-firstPts);
        if(duration>0) loop=Math.max(loop,timeline/duration);
        lastPts=pts;lastTimeline=timeline;
        return new Frame(session,loop,current,++frame,pts,timeline);
    }
    public static final class Frame {
        public final long session,loop,number,pts,timeline;
        public final int content;
        Frame(long session,long loop,int content,long number,long pts,long timeline) {
            this.session=session;this.loop=loop;this.content=content;
            this.number=number;this.pts=pts;this.timeline=timeline;
        }
    }
}
