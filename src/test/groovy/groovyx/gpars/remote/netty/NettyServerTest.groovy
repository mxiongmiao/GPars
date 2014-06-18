// GPars - Groovy Parallel Systems
//
// Copyright © 2014 The original author or authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package groovyx.gpars.remote.netty

class NettyServerTest extends GroovyTestCase implements NettyTest {
    public void testServerStart() {
        NettyServer server = new NettyServer(null, LOCALHOST_ADDRESS, LOCALHOST_PORT, null)
        server.start()
        server.channelFuture.sync()

        assert server.channelFuture.isSuccess()

        server.stop()
    }

    public void testServerCannotBeStoppedIfNotRunning() {
        NettyServer server = new NettyServer(null, LOCALHOST_ADDRESS, LOCALHOST_PORT, null)

        def message = shouldFail(IllegalStateException.class, {
            server.stop()
        })

        assert message == "Server has not been started"
    }

    public void testOnlyOneInstanceOfServerStarts() {
        NettyServer server1 = new NettyServer(null, LOCALHOST_ADDRESS, LOCALHOST_PORT, null)
        NettyServer server2 = new NettyServer(null, LOCALHOST_ADDRESS, LOCALHOST_PORT, null)

        server1.start()
        server2.start()

        shouldFail(BindException, {
            [server1.channelFuture, server2.channelFuture]*.sync()
        })

        [server1, server2]*.stop()
    }
}
