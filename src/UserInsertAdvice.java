import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.mdm.core.bean.pojo.BusinessLog;
import com.mdm.core.util.MdmException;
import com.mdm.pojo.UserBasicsInfo;

@Aspect
@Component
public class UserInsertAdvice {

	private static Logger logger = LoggerFactory.getLogger(ApiMethodTimeActive.class);
	
	private final String rule = "execution(* com.mdm.dao.write.user..*.*(..)) ";
	
	@Autowired
	private RedisTemplate redisTemplate;

	private static String redisCode = "utf-8";

	@Before(rule) // 此处为pointcut
	public void before(JoinPoint joinPoint) throws MdmException {
		// 每一个符合表达式条件的位置为joinPoint
		Object[] args = joinPoint.getArgs();
		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		Method method = signature.getMethod();
		// 拦截的实体类
		Object target = joinPoint.getTarget();
		String className = target.getClass().getName();
		logger.debug("className={}", className);
		BusinessLog businessLog = new BusinessLog();
		// 数据操作类型-删除
		if (method.getName().indexOf("insertUserBasicsInfo") >= 0) {
			UserBasicsInfo userInfoRequest  = (UserBasicsInfo)args[0];
			String key = userInfoRequest.getCellPhone();
			String value = "true";
			long returnValue = (Long)redisTemplate.execute(new RedisCallback() {
				@Override
				public Object doInRedis(RedisConnection connection) throws DataAccessException {
					if(connection.setNX(key.getBytes(), value.getBytes())){
						connection.expire(key.getBytes(), 10L);						
						return 1L;
					}
					return 0L;
					
				}
			});
			
			if(returnValue == 0L){
				throw new MdmException("禁止重新提交操作");
			}

		}

	}

	/**
	 * @param key
	 * @return
	 */
	public boolean exists(final String key) {
		return (boolean) redisTemplate.execute(new RedisCallback() {
			public Boolean doInRedis(RedisConnection connection) throws DataAccessException {
				return connection.exists(key.getBytes());
			}
		});
	}

	public String get(final String key) {
		return (String) redisTemplate.execute(new RedisCallback() {
			public String doInRedis(RedisConnection connection) throws DataAccessException {
				try {
					return new String(connection.get(key.getBytes()), redisCode);
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				}
				return "";
			}
		});
	}

	/**
	 * @param key
	 * @param value
	 * @param liveTime
	 */
	public void set(String key, String value, long liveTime) {
		this.set(key.getBytes(), value.getBytes(), liveTime);
	}

	public void set(final byte[] key, final byte[] value, final long liveTime) {
		redisTemplate.execute(new RedisCallback() {
			public Long doInRedis(RedisConnection connection) throws DataAccessException {
				connection.set(key, value);
				if (liveTime > 0) {
					connection.expire(key, liveTime);
				}
				return 1L;
			}
		});
	}

}
