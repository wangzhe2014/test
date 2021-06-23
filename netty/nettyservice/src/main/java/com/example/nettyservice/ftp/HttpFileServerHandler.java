package com.example.nettyservice.ftp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.CharsetUtil;

import javax.activation.MimetypesFileTypeMap;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.net.URLDecoder;
import java.util.regex.Pattern;

public class HttpFileServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Pattern INSECURE_URI = Pattern.compile(".*[<>&\"].*");
    private static final Pattern ALLOWED_FILE_NAME=Pattern.compile("[A-Za-z0-9][-_A-Za-z0-9\\.]*");

    private String url;
    public HttpFileServerHandler(String url) {
        this.url = url;
    }

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {

        if (!request.decoderResult().isSuccess()) {
            sendError(ctx, HttpResponseStatus.BAD_REQUEST);
            return;
        }
        if (request.method() != HttpMethod.GET) {
            sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
            return;
        }

        final String uri = request.uri();
        final String path = sanitizeUri(uri);
        if (path == null) {
            sendError(ctx, HttpResponseStatus.FORBIDDEN);
            return;
        }
        File file = new File(path);
        if (file.isHidden() || !file.exists()) {
            sendError(ctx, HttpResponseStatus.NOT_FOUND);
            return;
        }
        //如果是目录就发送目录的链接给客户端
        if(file.isDirectory()){
            if(uri.endsWith("/")){
                sendListing(ctx,file);
            }else {
                sendRedirect(ctx, uri+"/");
            }
            return;
        }
        //判断文件合法性
        if(!file.isFile()){
            sendError(ctx,HttpResponseStatus.FORBIDDEN);
            return;
        }

        RandomAccessFile randomAccessFile = null;
        try {
            //以只读的方式打开
            randomAccessFile = new RandomAccessFile(file, "r");
        }catch (FileNotFoundException fnfe) {
            sendError(ctx, HttpResponseStatus.NOT_FOUND);
        }

        long fileLength = randomAccessFile.length();
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        HttpHeaderUtil.setContentLength(response, fileLength);
        setContentTypeHeader(response,file);
        //判断是否是keepAlive，如果是就在响应头中设置CONNECTION为keepAlive
        if(HttpHeaderUtil.isKeepAlive(request)){
            response.headers().set(HttpHeaderNames.CONNECTION,HttpHeaderValues.KEEP_ALIVE);
        }
        ctx.write(response);
        ChannelFuture sendFileFuture;
        //通过Netty的ChunkedFile对象直接将文件写入到发送缓冲区中
        sendFileFuture=ctx.write(new ChunkedFile(randomAccessFile,0,fileLength,8192),ctx.newProgressivePromise());
        //为sendFileFuture添加监听器，如果发送完成打印发送完成的日志
        sendFileFuture.addListener(new ChannelProgressiveFutureListener() {
            @Override
            public void operationComplete(ChannelProgressiveFuture future)throws Exception{
                System.out.println("Transfer complete.");
            }

            @Override
            public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) throws Exception
            {
                if(total<0){
                    System.err.println("Transfer progress: "+progress);
                }else {
                    System.err.println("Transfer progress: "+progress+"/"+total);
                }
            }
        });
        //如果使用chunked编码，最后需要发送一个编码结束的空消息体，将LastHttpContent.EMPTY_LAST_CONTENT发送到缓冲区中，
        //来标示所有的消息体已经发送完成，同时调用flush方法将发送缓冲区中的消息刷新到SocketChannel中发送
        ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        //如果是非keepAlive的，最后一包消息发送完成后，服务端要主动断开连接
        if(!HttpHeaderUtil.isKeepAlive(request)){
            lastContentFuture.addListener(ChannelFutureListener.CLOSE);
        }

    }

    //发送目录的链接到客户端浏览器
    private static void sendListing(ChannelHandlerContext ctx, File dir){
        //创建成功的http响应消息
        FullHttpResponse response=new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        //设置消息头的类型是html文件，不要设置为text/plain，客户端会当做文本解析
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html;charset=UTF-8");
        //构造返回的html页面内容
        StringBuilder buf=new StringBuilder();
        String dirPath=dir.getPath();
        buf.append("<!DOCTYPE html>\r\n");
        buf.append("<html><head><title>");
        buf.append(dirPath);
        buf.append("目录：");
        buf.append("</title></head><body>\r\n");
        buf.append("<h3>");
        buf.append(dirPath).append("目录：");
        buf.append("</h3>\r\n");
        buf.append("<ul>");
        buf.append("<li>链接：<a href=\"../\">..</a></li>\r\n");
        for(File f:dir.listFiles()){
            if(f.isHidden()||!f.canRead()){
                continue;
            }
            String name=f.getName();
            if(!ALLOWED_FILE_NAME.matcher(name).matches()){
                continue;
            }
            buf.append("<li>链接：<a href=\"");
            buf.append(name);
            buf.append("\">");
            buf.append(name);
            buf.append("</a></li>\r\n");
        }
        buf.append("</ul></body></html>\r\n");
        //分配消息缓冲对象
        ByteBuf buffer=Unpooled.copiedBuffer(buf,CharsetUtil.UTF_8);
        //将缓冲区的内容写入响应对象，并释放缓冲区
        response.content().writeBytes(buffer);
        buffer.release();
        //将响应消息发送到缓冲区并刷新到SocketChannel中
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static void sendRedirect(ChannelHandlerContext ctx,String newUri){
        FullHttpResponse response=new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND);
        response.headers().set(HttpHeaderNames.LOCATION, newUri);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }


    private static void sendError(ChannelHandlerContext ctx, HttpResponseStatus status){
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,status,
                Unpooled.copiedBuffer("Failure: "+status.toString()+"\r\n", CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE,"text/html;charset=UTF-8");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private String sanitizeUri(String uri){
        try{
            //使用UTF-8对URL进行解码
            uri= URLDecoder.decode(uri,"UTF-8");
        }catch (Exception e){
            try{
                //解码失败就使用ISO-8859-1进行解码
                uri=URLDecoder.decode(uri,"ISO-8859-1");
            }catch (Exception e2) {
                //仍然失败就返回错误
                throw new Error();
            }
        }
        //解码成功后对uri进行合法性判断，避免访问无权限的目录
        if(!uri.startsWith(url)){
            return null;
        }
        if(!uri.startsWith("/")){
            return null;
        }
        //将硬编码的文件路径分隔符替换为本地操作系统的文件路径分隔符
        uri= uri.replace('/', File.separatorChar);
        if(uri.contains(File.separator +".")||uri.contains('.'+File.separator)||
                uri.startsWith(".")||uri.endsWith(".") || INSECURE_URI.matcher(uri).matches()){
            return null;
        }
        //使用当前运行程序所在的工程目录+URI构造绝对路径
        return System.getProperty("user.dir")+File.separator+uri;
    }

    private static void setContentTypeHeader(HttpResponse response,File file){
        MimetypesFileTypeMap mimetypesTypeMap=new MimetypesFileTypeMap();
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, mimetypesTypeMap.getContentType(file.getPath()));
    }
}
