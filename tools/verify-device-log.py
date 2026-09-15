#!/usr/bin/env python3
"""Validate real adb logcat captured during both touch-exit scenarios and repeated starts."""
import re,sys,json
from pathlib import Path
sessions={};exits={};released=set();cleaned=set();observed=set()
for line in Path(sys.argv[1]).read_text(errors='replace').splitlines():
    m=re.search(r'Playlist session=(\d+) selected=([123]) candidates=2',line)
    if m:
        session,first=map(int,m.groups())
        assert session not in sessions,'Capture must contain one service process lifetime'
        sessions[session]={'first':first,'maxLoop':0,'frames':0,'lastFrame':0,'lastTimeline':-1};observed.add(first)
    m=re.search(r'Shared session=(\d+) loop=(\d+) content=([123]) frame=(\d+) pts=(-?\d+) timeline=(\d+) targets=(.*)',line)
    if m:
        session,item,content,frame,pts,timeline=map(int,m.groups()[:6]);targets=m.group(7).split()
        assert targets==['D2','D0'],'A rendered frame did not reach both panels'
        assert session not in released,'Frame rendered after session release'
        info=sessions[session]
        assert content==info['first'],'Content changed within a session'
        assert frame>info['lastFrame'] and timeline>=info['lastTimeline']
        assert item>=info['maxLoop'],'Loop counter regressed'
        info.update(frames=info['frames']+1,lastFrame=frame,lastTimeline=timeline,maxLoop=item)
    m=re.search(r'Exit requested session=(\d+) source=(D[02]-touch) targets=D2 D0',line)
    if m:
        session=int(m[1]);assert session not in exits,'Exit re-entered';exits[session]=m[2]
    m=re.search(r'Session released session=(\d+) frames=\d+ targets=0 player=none decoderSurface=none',line)
    if m:released.add(int(m[1]))
    m=re.search(r'Cleanup complete session=(\d+) targets=D2 D0 decoderReleased=true',line)
    if m:cleaned.add(int(m[1]))
assert observed=={1,3},'Need actual sessions selecting each of A/C'
assert all(any(x['first']==choice and x['maxLoop']>=2 for x in sessions.values()) for choice in (1,3)), 'Need at least two loop boundaries for each selected video'
assert {'D0-touch','D2-touch'}<=set(exits.values()),'Both panels must be touched in separate sessions'
for session in exits:
    assert session in released and session in cleaned,'Touch session did not fully release'
    assert sessions[session]['frames']>0,'Touch exit session had no video frame evidence'
print(json.dumps({'log_checks':'PASS','sessions':sessions,'touch_exits':exits,
    'note':'Logs alone do not prove desktop restoration or lack of visual flashes; inspect corresponding D2/D0 screenshots and recordings.'},indent=2))
