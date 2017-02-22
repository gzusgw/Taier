package com.dtstack.rdos.engine.execution.base.operator;

/**
 * 
 * Reason: TODO ADD REASON(可选)
 * Date: 2016年02月22日 下午1:16:37
 * Company: www.dtstack.com
 * @author sishu.yss
 *
 */
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Properties;

public class ParamsOperator implements Operator{
	
	private Properties properties;

	@Override
	public boolean createOperator(String sql) throws Exception {
		// TODO Auto-generated method stub
		properties = new Properties(); 
        InputStream   inputStream   =  new  ByteArrayInputStream(sql.trim().getBytes());
        properties.load(inputStream);
		return true;
	}

	public Properties getProperties() {
		return properties;
	}

	@Override
	public boolean verification(String sql) throws Exception {
		// TODO Auto-generated method stub
		return false;
	}
}