package com.deep007.mitmproxyjava.grpc;

import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.deep007.mitmproxyjava.MitmRequest;
import com.deep007.mitmproxyjava.MitmResponse;
import com.deep007.mitmproxyjava.mitmproxy.RemoteMitmproxy;
import com.deep007.mitmproxyjava.modle.FlowRequest;
import com.deep007.mitmproxyjava.modle.FlowResponse;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

public class MitmproxyFlowCallBackServer {
	
	private volatile static MitmproxyFlowCallBackServer mitmFlowCallBackServer;
	
	/**
	 * 获取默认的MitmproxyFlowCallBackServer实例，默认的实例启动在60061端口上。如果你想启动在其他端口请使用MitmproxyFlowCallBackServer(int port)构造方法创建
	 * 并注意维护其生命周期
	 * @return
	 */
	public synchronized static MitmproxyFlowCallBackServer getInstance() {
		if (mitmFlowCallBackServer == null) {
			try {
				mitmFlowCallBackServer = new MitmproxyFlowCallBackServer();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return mitmFlowCallBackServer;
	}
	
	private Server server;
	
	public final int port;
	
	private boolean isStarted = false;
	
	public MitmproxyFlowCallBackServer() throws Exception {
		this(60061);
	}
	
	
	public MitmproxyFlowCallBackServer(int port) throws Exception {
		this.port = port;
		this.server = ServerBuilder.forPort(port)
				.addService(new MonitorServerImpl())
				.build();
		start();
	}
	
	
	public int getPort() {
		return port;
	}
	
	private void start() throws Exception {
		if (!isStarted) {
			server.start();
			Class<?> nettyServerHandlerClass =  Class.forName("io.grpc.netty.shaded.io.grpc.netty.NettyServerHandler");
			Field loggerField = nettyServerHandlerClass.getDeclaredField("logger");
			loggerField.setAccessible(true);
			java.util.logging.Logger log = (Logger) loggerField.get(null);
			log.setLevel(Level.SEVERE);
			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					MitmproxyFlowCallBackServer.this.stop();
				}
			});
			isStarted = true;
			System.out.println("MitmproxyFlowCallBackServer started on "+ port);
		}
		
	}
 
	private void stop() {
		if (isStarted && server != null) {
			server.shutdownNow();
			server = null;
		}
	}
	
	private class MonitorServerImpl extends MitmProxyHubClientServerGrpc.MitmProxyHubClientServerImplBase {


		@Override
		public void onMitmRequest(MitmRequest request, StreamObserver<MitmRequest> responseObserver) {
			String mitmproxyId = request.getMitmproxyId();
			System.out.println("remotemitmproxy " + request.getMethod() + " - " + request.getUrl());
			FlowRequest lRequest = FlowRequest.create(request);
			RemoteMitmproxy remoteMitmproxy = RemoteMitmproxy.RemoteMitmproxies.get(mitmproxyId);
			if (remoteMitmproxy != null) {
				remoteMitmproxy.onRequest(lRequest);
			}
			responseObserver.onNext(lRequest.getMitmRequest());
			responseObserver.onCompleted();
		}
		
		@Override
		public void onMitmResponse(MitmResponse response, StreamObserver<MitmResponse> responseObserver) {
			String mitmproxyId = response.getMitmproxyId();
			RemoteMitmproxy remoteMitmproxy = RemoteMitmproxy.RemoteMitmproxies.get(mitmproxyId);
			FlowResponse lResponse = FlowResponse.create(response);
			if (remoteMitmproxy != null) {
				remoteMitmproxy.onResponse(lResponse);
			}
			responseObserver.onNext(lResponse.getMitmResponse());
			responseObserver.onCompleted();
		}
		
	}
	
	
}
