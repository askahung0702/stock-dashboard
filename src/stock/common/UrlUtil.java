package stock.common;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class UrlUtil {
    public static void DownLoadPages(String urlStr, String outPath){
    	int chByte = 0;
    	URL url = null;
    	HttpURLConnection httpConn = null;
    	InputStream in = null;
    	FileOutputStream out = null;
    	try{
    		url = new URL(urlStr);
    		httpConn = (HttpURLConnection) url.openConnection();
    		HttpURLConnection.setFollowRedirects(true);
    		httpConn.setRequestMethod("GET"); 
    		httpConn.setRequestProperty("User-Agent","Mozilla/4.0 (compatible; MSIE 6.0; Windows 2000)"); 

    		in = httpConn.getInputStream();
    		out = new FileOutputStream(new File(outPath));
    		chByte = in.read();
    		while (chByte != -1){
    			out.write(chByte);
    			chByte=in.read();
    		}
    	}catch (MalformedURLException e){
    		e.printStackTrace();
    	}catch (IOException e){
    		e.printStackTrace();
    	}
    }
    
    public static InputStream DownLoadPages(String urlStr){

    	URL url = null;
    	HttpURLConnection httpConn = null;
    	InputStream in = null;
    	
    	try{
    		url = new URL(urlStr);
    		httpConn = (HttpURLConnection) url.openConnection();
    		HttpURLConnection.setFollowRedirects(true);
    		httpConn.setRequestMethod("GET"); 
    		httpConn.setRequestProperty("User-Age"
    				+ ""
    				+ ""
    				+ "nt","Mozilla/4.0 (compatible; MSIE 6.0; Windows 2000)"); 

    		in = httpConn.getInputStream();
    	}catch (MalformedURLException e){
    		e.printStackTrace();
    	}catch (IOException e){
    		e.printStackTrace();
    	}
    	return in;
    }
}
