package dev.denver.dressroomadvanced;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class DeferredDiscoveryTest {
    private static int checks;
    private static void check(boolean ok) {if(!ok)throw new AssertionError("Discovery check "+checks);checks++;}
    public static void main(String[] args) throws Exception {
        ArrayDeque<Runnable> jobs=new ArrayDeque<>(); List<Object> installed=new ArrayList<>();
        DeferredDiscovery<Object> d=new DeferredDiscovery<>(jobs::add,key->{installed.add(key);return true;});
        Object a=new Object(),b=new Object();
        d.offer(a);check(installed.isEmpty());check(jobs.size()==1);
        d.offer(a);check(jobs.size()==1);
        jobs.remove().run();check(installed.size()==1);
        d.offer(a);check(jobs.isEmpty());
        d.offer(b);check(installed.size()==1);jobs.remove().run();check(installed.get(1)==b);
        int[] attempts={0};
        DeferredDiscovery<Object> retry=new DeferredDiscovery<>(jobs::add,key->++attempts[0]>1);
        retry.offer(a);jobs.remove().run();retry.offer(a);check(jobs.size()==1);jobs.remove().run();
        retry.offer(a);check(jobs.isEmpty());
        @SuppressWarnings("unchecked") DeferredDiscovery<Object>[] recursive=new DeferredDiscovery[1];
        recursive[0]=new DeferredDiscovery<>(jobs::add,key->{recursive[0].offer(key);return true;});
        recursive[0].offer(a);jobs.remove().run();check(jobs.isEmpty());
        List<Runnable> concurrent=java.util.Collections.synchronizedList(new ArrayList<>());
        DeferredDiscovery<Object> parallel=new DeferredDiscovery<>(concurrent::add,key->true);
        Thread x=new Thread(()->parallel.offer(a)),y=new Thread(()->parallel.offer(a));
        x.start();y.start();x.join();y.join();check(concurrent.size()==1);
        concurrent.get(0).run();parallel.offer(a);check(concurrent.size()==1);
        System.out.println("Deferred provider discovery: "+checks+" checks passed");
    }
}
