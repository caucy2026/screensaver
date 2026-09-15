import com.kemi.dualscreensaver.PlaybackSequence;
import java.util.Random;
public final class PlaybackSequenceCheck {
    static void check(boolean value,String reason) { if(!value) throw new AssertionError(reason); }
    static final class Choice extends Random {
        int calls;final int chosen;
        Choice(int chosen) { this.chosen=chosen; }
        @Override public int nextInt(int bound) { check(bound==3,"Not three candidates");calls++;return chosen; }
    }
    public static void main(String[] args) {
        for(int selected=0;selected<2;selected++) {
            final int pick=selected;
            Random r=new Random(){@Override public int nextInt(int bound){check(bound==2,"Two candidates required");return pick;}};
            PlaybackSequence q=new PlaybackSequence(r,new int[]{1,3});q.start();q.duration(125_000_000L);
            for(int loop=0;loop<100;loop++) for(long pts:new long[]{0,41_666_667L,83_333_333L}) {
                check(q.acquired(pts).content==(pick==0?1:3),"B selected or choice changed");
            }
        }

        for(int first=0;first<3;first++) {
            Choice choice=new Choice(first);PlaybackSequence p=new PlaybackSequence(choice);p.start();p.duration(125_000_000L);
            check(p.current()==first+1,"Wrong selected video");long number=0,time=-1;
            for(int loop=0;loop<100;loop++) {
                for(long pts:new long[]{0,41_666_667L,83_333_333L}) {
                    PlaybackSequence.Frame f=p.acquired(pts);
                    check(f!=null && f.content==first+1,"Content changed within session");
                    check(f.loop==loop && f.number==++number && f.timeline>time,"Frame or loop timeline wrong");time=f.timeline;
                    check(p.acquired(pts)==null,"Duplicate frame accepted");
                }
                check(choice.calls==1,"Randomized again inside session");
            }
            long session=p.session();p.stop();p.stop();check(p.acquired(99)==null,"Frame accepted after exit");
            p.start();check(choice.calls==2 && p.frame()==0 && !p.matches(session),"New session not isolated");
        }
        PlaybackSequence continuous=new PlaybackSequence(new Random(3));continuous.start();continuous.duration(125_000_000L);
        for(int i=0;i<10;i++) check(continuous.acquired(i*125_000_000L).loop==i,"Continuous native PTS loop count");
        PlaybackSequence random=new PlaybackSequence(new Random(20260913));boolean[] seen=new boolean[3];
        for(int i=0;i<100;i++) {random.start();seen[random.current()-1]=true;random.stop();}
        check(seen[0]&&seen[1]&&seen[2],"Did not exercise all three selections");
        System.out.println("PASS: three random choices, 100 fixed-content loops each, one random call per session, looping/continuous PTS and exit isolation");
    }
}
