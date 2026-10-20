package gs_passportization;

import java.io.IOException;
import java.net.URL;
import java.util.Properties;

import org.hibernate.mapping.Set;

public class LogСonfigUtil {
	
	public static void set_p6spyConfig() {
		URL propertiesFile = GSP_App.class.getClassLoader().getResource("spy.properties");
		Properties properties = new Properties();
		try {
			properties.load(propertiesFile.openStream());
		} catch (IOException e) { e.printStackTrace(); }
		
		//System.out.println(properties.getProperty("logfile"));
		
	}
	
	public static void set_log4j2Config() {}

}
