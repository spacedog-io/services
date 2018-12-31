package io.spacedog.services.job;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.amazonaws.services.cloudwatchevents.AmazonCloudWatchEvents;
import com.amazonaws.services.cloudwatchevents.AmazonCloudWatchEventsClient;
import com.amazonaws.services.cloudwatchevents.model.DeleteRuleRequest;
import com.amazonaws.services.cloudwatchevents.model.PutRuleRequest;
import com.amazonaws.services.cloudwatchevents.model.PutTargetsRequest;
import com.amazonaws.services.cloudwatchevents.model.RemoveTargetsRequest;
import com.amazonaws.services.cloudwatchevents.model.RuleState;
import com.amazonaws.services.cloudwatchevents.model.Target;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClient;
import com.amazonaws.services.lambda.model.CreateFunctionRequest;
import com.amazonaws.services.lambda.model.DeleteFunctionRequest;
import com.amazonaws.services.lambda.model.Environment;
import com.amazonaws.services.lambda.model.FunctionCode;
import com.amazonaws.services.lambda.model.FunctionConfiguration;
import com.amazonaws.services.lambda.model.GetFunctionRequest;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.model.ResourceNotFoundException;
import com.amazonaws.services.lambda.model.UpdateFunctionCodeRequest;
import com.amazonaws.services.lambda.model.UpdateFunctionConfigurationRequest;

import io.spacedog.client.http.ContentTypes;
import io.spacedog.client.http.SpaceStatus;
import io.spacedog.client.job.LambdaJob;
import io.spacedog.server.Server;
import io.spacedog.server.ServerConfig;
import io.spacedog.utils.Utils;
import net.codestory.http.payload.Payload;

public class JobService {

	private static final String TARGET_ID = "target-0";
	private static final String ROLE_SPACEDOG_JOB = "arn:aws:iam::309725721660:role/spacedog-job";
	private static final String ROLE_SPACEDOG_JOB_RULE = "arn:aws:iam::309725721660:role/spacedog-job-rule";

	private static AWSLambda lambda;
	private static AmazonCloudWatchEvents events;

	static {
		lambda = AWSLambdaClient.builder()//
				.withRegion(ServerConfig.awsRegion())//
				.build();

		events = AmazonCloudWatchEventsClient.builder()//
				.withRegion(ServerConfig.awsRegion())//
				.build();
	}

	public List<LambdaJob> list() {
		return lambda.listFunctions().getFunctions().stream()//
				.filter(function -> function.getFunctionName().startsWith(functionNamePrefix()))//
				.map(function -> toJob(function))//
				.collect(Collectors.toList());
	}

	public Optional<LambdaJob> get(String jobName) {
		try {
			return Optional.of(toJob(lambda.getFunction(//
					new GetFunctionRequest() //
							.withFunctionName(functionName(jobName)))//
					.getConfiguration()));

		} catch (ResourceNotFoundException e) {
			return Optional.empty();
		}
	}

	public void save(LambdaJob job) {
		String arn = null;
		Optional<LambdaJob> oldJob = get(job.name);
		if (oldJob.isPresent()) {
			arn = lambda.updateFunctionConfiguration(toUpdateFunctionConfigurationRequest(job))//
					.getFunctionArn();

			lambda.updateFunctionCode(new UpdateFunctionCodeRequest()//
					.withFunctionName(functionName(job.name))//
					.withZipFile(ByteBuffer.wrap(job.code)));
		} else {
			arn = lambda.createFunction(toCreateFunctionRequest(job)).getFunctionArn();
		}

		setJobRule(job, arn);
	}

	public String getCodeLocation(String jobName) {
		return lambda.getFunction(//
				new GetFunctionRequest()//
						.withFunctionName(functionName(jobName)))//
				.getCode()//
				.getLocation();
	}

	public void setCode(String jobName, byte[] bytes) {
		lambda.updateFunctionCode(//
				new UpdateFunctionCodeRequest()//
						.withFunctionName(functionName(jobName))//
						.withZipFile(ByteBuffer.wrap(bytes)));
	}

	public void delete(String jobName) {
		lambda.deleteFunction(//
				new DeleteFunctionRequest()//
						.withFunctionName(functionName(jobName)));

		events.removeTargets(new RemoveTargetsRequest()//
				.withRule(eventRuleName(jobName))//
				.withIds(TARGET_ID));

		events.deleteRule(new DeleteRuleRequest()//
				.withName(eventRuleName(jobName)));
	}

	public Payload invoke(String jobName, byte[] payload) {
		InvokeResult result = lambda.invoke(new InvokeRequest()//
				.withFunctionName(functionName(jobName))//
				.withPayload(ByteBuffer.wrap(payload)));

		String error = result.getFunctionError();
		int status = error == null ? SpaceStatus.CREATED//
				: error.equals("Handled") ? SpaceStatus.BAD_REQUEST //
						: SpaceStatus.INTERNAL_SERVER_ERROR;

		return new Payload(ContentTypes.JSON_UTF8, //
				result.getPayload().array(), //
				status);
	}

	public void deleteExecution(String jobName, String jobExecId) {
		throw new UnsupportedOperationException();
	}

	//
	// Implementation
	//

	private String functionNamePrefix() {
		return Server.backend().id() + '-';
	}

	private String functionName(String jobName) {
		return functionNamePrefix() + jobName;
	}

	private String eventRuleName(String name) {
		return functionName(name) + "-rule";
	}

	private LambdaJob toJob(FunctionConfiguration configuration) {
		LambdaJob job = new LambdaJob();
		job.name = Utils.trimPreffix(configuration.getFunctionName(), functionNamePrefix());
		job.handler = configuration.getHandler();
		job.env = configuration.getEnvironment().getVariables();
		job.description = configuration.getDescription();
		job.memoryInMBytes = configuration.getMemorySize();
		job.timeoutInSeconds = configuration.getTimeout();
		return job;
	}

	private UpdateFunctionConfigurationRequest toUpdateFunctionConfigurationRequest(LambdaJob job) {
		return new UpdateFunctionConfigurationRequest()//
				.withFunctionName(functionName(job.name))//
				.withEnvironment(new Environment().withVariables(job.env))//
				.withHandler(job.handler)//
				.withDescription(job.description)//
				.withTimeout(job.timeoutInSeconds)//
				.withMemorySize(job.memoryInMBytes);
	}

	private CreateFunctionRequest toCreateFunctionRequest(LambdaJob job) {
		return new CreateFunctionRequest()//
				.withFunctionName(functionName(job.name))//
				.withEnvironment(new Environment().withVariables(job.env))//
				.withHandler(job.handler)//
				.withDescription(job.description)//
				.withTimeout(job.timeoutInSeconds)//
				.withMemorySize(job.memoryInMBytes)//
				.withCode(new FunctionCode()//
						.withZipFile(ByteBuffer.wrap(job.code)))//
				.withRuntime("java8")//
				.withRole(ROLE_SPACEDOG_JOB);
	}

	private void setJobRule(LambdaJob job, String arn) {
		String eventRuleName = eventRuleName(job.name);

		events.putRule(new PutRuleRequest()//
				.withName(eventRuleName)//
				.withRoleArn(ROLE_SPACEDOG_JOB_RULE)//
				.withScheduleExpression(job.when)//
				.withDescription(job.when)//
				.withState(RuleState.ENABLED));

		events.putTargets(new PutTargetsRequest()//
				.withRule(eventRuleName)//
				.withTargets(new Target()//
						.withId(TARGET_ID)//
						.withArn(arn)));
	}

}
