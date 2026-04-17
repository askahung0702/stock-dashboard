package stock.common;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.GeneralSecurityException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

public class HtmlUtil {

	  protected static final String ENCODING = "UTF-8";

	  protected static String apiKey;
	  private static String referrer;

	  protected static final String PARAM_API_KEY = "key=",
	      PARAM_LANG_PAIR = "&lang=",
	      PARAM_TEXT = "&text=";	
	
	public static String despace(String htmlString) throws Exception{
		
		htmlString = htmlString.replace("\n", "");
		htmlString = htmlString.replace(" ", "");
		htmlString = htmlString.replace("�@", "");
		htmlString = htmlString.replace("	", "");
		htmlString = htmlString.replace("	", "");
		htmlString = htmlString.replace("\"", "");
		htmlString = htmlString.replace("<spanclass=clr-rd>", "");
		htmlString = htmlString.replace("<spanclass=clr-gr>", "");
		htmlString = htmlString.replace("</span>", "");
		return htmlString;
	}
	
	public static float numeralization(String str,String replaceStr) throws Exception{
		
		try{
			return Float.valueOf(str.replace(replaceStr, "")) ;
			
		}catch (Exception e) {
			// TODO: handle exception
			if (str.replace(replaceStr, "").equals("������")){
				return Float.valueOf(-100);
			}else if (str.replace(replaceStr, "").equals("�����")){
				return Float.valueOf(100);
			}else{
				return Float.valueOf(1);
			}
		}
	}	
	
	public static String pageSplit(String htmlString,String str1,int int1,String str2,int int2,String str3) throws Exception{
		
		String str = "";		
		
		str = htmlString.split(str1)[int1].split(str2)[int2].split(str3)[0];
				
		return str;
	}	
	
	public static String parserHtml(String url) throws Exception{
			Document doc = null;
			String xmlResponse = null;
			
			TrustManager[] trustAllCerts = new TrustManager[] { 
				    new X509TrustManager() {    
				        public java.security.cert.X509Certificate[] getAcceptedIssuers() { 
				            return null; 
				        } 
				        public void checkClientTrusted( 
				            java.security.cert.X509Certificate[] certs, String authType) {
				            } 
				        public void checkServerTrusted( 
				            java.security.cert.X509Certificate[] certs, String authType) {
				        }
				    } 
				}; 		  
			try {
			    SSLContext sc = SSLContext.getInstance("SSL"); 
			    sc.init(null, trustAllCerts, new java.security.SecureRandom()); 
			    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
			} catch (GeneralSecurityException e) {
				System.out.println("GeneralSecurityException"+e);
				
			} 
			// Now you can access an https URL without having the certificate in the truststore
		
			
		try {
			
			URL url1 = new URL(url);
			// need http protocol	
			doc = Jsoup.connect(url).get();
			Elements tbodys =doc.select("body > table > tbody");
			
		} catch (Exception e) {
			
			return "error : "+e;
		}
		return doc.toString();
	}
	
	public static String parserHtmlForJson(String url) throws Exception{
		Document doc = null;
		TrustManager[] trustAllCerts = new TrustManager[] { 
			    new X509TrustManager() {    
			        public java.security.cert.X509Certificate[] getAcceptedIssuers() { 
			            return null; 
			        } 
			        public void checkClientTrusted( 
			            java.security.cert.X509Certificate[] certs, String authType) {
			            } 
			        public void checkServerTrusted( 
			            java.security.cert.X509Certificate[] certs, String authType) {
			        }
			    } 
			}; 		  
		try {
		    SSLContext sc = SSLContext.getInstance("SSL"); 
		    sc.init(null, trustAllCerts, new java.security.SecureRandom()); 
		    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		} catch (GeneralSecurityException e) {
		} 
		// Now you can access an https URL without having the certificate in the truststore
	
		URL urlForJson = new URL(url);
	    final HttpsURLConnection uc = (HttpsURLConnection) urlForJson.openConnection();
	    if(referrer!=null)
	      uc.setRequestProperty("referer", referrer);
	      uc.setRequestProperty("Content-Type","text/plain; charset=" + ENCODING);
	      uc.setRequestProperty("Accept-Charset",ENCODING);
	      uc.setRequestMethod("GET");

	    try {
	      final int responseCode = uc.getResponseCode();
	      final String result = inputStreamToString(uc.getInputStream());
	      if(responseCode!=200) {
	        throw new Exception("Error from Yandex API: " + result);
	      }
	      return result;
	    } catch (Exception ex) {
	      return "{\"code\":200,\"lang\":\"en-zh\",\"text\":[\"error\"]}";
	    }finally { 
	      if(uc!=null) {
	        uc.disconnect();
	      }
	    }
    }
	private static String inputStreamToString(final InputStream inputStream) throws Exception {
		    final StringBuilder outputBuilder = new StringBuilder();

		    try {
		      String string;
		      if (inputStream != null) {
		        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, ENCODING));
		        while (null != (string = reader.readLine())) {
		          // TODO Can we remove this?
		          // Need to strip the Unicode Zero-width Non-breaking Space. For some reason, the Microsoft AJAX
		          // services prepend this to every response
		          outputBuilder.append(string.replaceAll("\uFEFF", ""));
		        }
		      }
		    } catch (Exception ex) {
		      throw new Exception("[yandex-translator-api] Error reading translation stream.", ex);
		    }
		    return outputBuilder.toString();
	}		
	public static void main(String[] args) throws Exception
	{
	
		System.out.println(parserHtmlForJson("https://translate.yandex.net/api/v1.5/tr.json/translate?key=trnsl.1.1.20160620T052531Z.6407a2236456b7ba.01c15242eed3fc628bbca73973d0fa6dae015186&lang=zh&text=Write off with Settlement greater than Claim"));
		
	}
	

	
}
