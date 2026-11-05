package dev.denver.dressroomadvanced;

/** Main-thread mailbox: one running job and one replacement descriptor, never queued bitmaps. */
final class LatestDepthWork<T> {
    interface Start<T>{void run(T request,Runnable complete);}
    private final Start<T> start;
    private T pending;
    private boolean running;
    LatestDepthWork(Start<T> start){this.start=start;}
    // Requests are cheap descriptors. Take the wallpaper bitmap only when a job starts;
    // otherwise a crop gesture can queue a pile of full-size snapshots behind detection.
    void offer(T value){pending=value;drain();}
    void clear(){pending=null;}
    private void drain(){if(running || pending==null)return;T next=pending;pending=null;running=true;
        final boolean[] completed={false};Runnable complete=()->{if(completed[0])return;completed[0]=true;running=false;drain();};
        try{start.run(next,complete);}catch(RuntimeException error){complete.run();throw error;}
    }
}
