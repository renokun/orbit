/*
Copyright (C) 2015 Electronic Arts Inc.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:

1.  Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.
2.  Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.
3.  Neither the name of Electronic Arts, Inc. ("EA") nor the names of
    its contributors may be used to endorse or promote products derived
    from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY ELECTRONIC ARTS AND ITS CONTRIBUTORS "AS IS" AND ANY
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL ELECTRONIC ARTS OR ITS CONTRIBUTORS BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.ea.orbit.actors.test;


import com.ea.orbit.actors.IActor;
import com.ea.orbit.actors.OrbitStage;
import com.ea.orbit.actors.runtime.IHosting;
import com.ea.orbit.actors.runtime.OrbitActor;
import com.ea.orbit.actors.test.actors.ISomeActor;
import com.ea.orbit.actors.test.actors.IStatelessThing;
import com.ea.orbit.concurrent.Task;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShutdownTest extends ActorBaseTest
{


    public interface IShut extends IActor
    {
        Task<Void> doSomething();

        Task<Void> doSomethingBlocking();
    }

    public static class Shut extends OrbitActor implements IShut
    {
        @Inject
        FakeSync fakeSync;

        public Task<Void> doSomething()
        {
            return Task.done();
        }

        public Task<Void> doSomethingBlocking()
        {
            fakeSync.put("executing", true);
            fakeSync.get("canFinish").join();
            return Task.done();
        }
    }

    @Test
    public void basicShutdownTest() throws ExecutionException, InterruptedException
    {
        OrbitStage stage1 = createStage();
        OrbitStage client = createClient();

        IActor.getReference(IShut.class, "0").doSomething().join();
        OrbitStage stage2 = createStage();

        stage1.stop().join();
        IActor.getReference(IShut.class, "0").doSomething().join();
    }


    @Test
    public void asyncShutdownTest() throws ExecutionException, InterruptedException
    {
        OrbitStage stage1 = createStage();
        OrbitStage client = createClient();

        Task<Void> methodCall = IActor.getReference(IShut.class, "0").doSomethingBlocking();
        // is blocked in the doSomethingBlocking
        assertEquals(true, fakeSync.get("executing").join());
        OrbitStage stage2 = createStage();
        assertFalse(methodCall.isDone());
        CompletableFuture<Void> stopFuture = CompletableFuture.runAsync(() -> stage1.stop().join());
        awaitFor(() -> stage1.getState() == IHosting.NodeState.STOPPING);

        // release doSomethingTo finish
        fakeSync.put("canFinish", true);
        stopFuture.join();

        assertTrue(methodCall.isDone());

    }


}
