package com.sdv.sync.reviewStateSynchronizer.api;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import com.atlassian.crucible.event.ReviewStateChangedEvent;
import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;

public class ReviewStateEventListener implements InitializingBean, DisposableBean {
	
	private static final Logger log = LoggerFactory.getLogger(ReviewStateEventListener.class); 
	
	private static final String propertiesFile = "ReviewStateSynchronizer.properties";
	
	private static final String originPropertyName = "sync.webhook.origin";
	
	
	@ComponentImport
	private final EventPublisher eventPublisher;	
	
	@Inject
	@Autowired
    public ReviewStateEventListener(EventPublisher eventPublisher) {
		super();
		this.eventPublisher = eventPublisher;
	}

	@EventListener
    public void onReviewStateChangedEvent(ReviewStateChangedEvent event) throws MalformedURLException, IOException, Exception {		
    	log.info("Review " + event.getReviewId().getId() + " has changed state from '" + event.getOldState().toString() + "' to '" + event.getNewState().toString() + "'");

    	String origin = null;    	
    	InputStream is = getClass().getClassLoader().getResourceAsStream(propertiesFile);    	
    	if (is != null)
    	{
    		Properties p = new Properties();
    		p.load(is);
    		
    		origin = p.getProperty(originPropertyName);
    	}
    	
    	if (origin == null)
    	{
    		log.error(originPropertyName + " not found in " + propertiesFile);
    		return;
    	}    	
    	
    	URL myUrl = new URL(origin + "/review/" + event.getReviewId().getId() + "/state");
    	String postData = "{\"old\":\"" + event.getOldState().toString() + "\", \"new\":\"" + event.getNewState().toString() + "\"} ";   	
  	
    	HttpURLConnection conn = (HttpURLConnection) myUrl.openConnection();
    	conn.setRequestMethod("POST");
    	conn.setDoInput(true);
    	conn.setDoOutput(true);
    	conn.setRequestProperty( "Content-Type", "application/json"); 
    	conn.setRequestProperty( "charset", "utf-8");
    	conn.setRequestProperty( "Content-Length", Integer.toString( postData.length()));   	 	

    	DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
		wr.writeBytes(postData);
		wr.flush();
		wr.close();
    	
		conn.connect();
	    int responseCode = conn.getResponseCode();
	    
	    log.info("Posting request about review " + event.getReviewId().getId() + " changed state has returned response with status code " + responseCode);
    }	

	@Override
	public void destroy() throws Exception {
		this.eventPublisher.unregister(this);
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		this.eventPublisher.register(this);		
	}
}
