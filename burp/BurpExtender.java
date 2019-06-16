package burp;

import com.eclipsesource.json.*;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BurpExtender implements IBurpExtender, IHttpListener {
	private PrintStream out, err;
	private IExtensionHelpers helpers;
	private Map<String, String> stageId;
	private static URL reportAPI = null;

	@Override
	public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks) {
		callbacks.setExtensionName("Arknights Reporter");
		callbacks.registerHttpListener(this);
		out = new PrintStream(callbacks.getStdout());
		err = new PrintStream(callbacks.getStderr());
		helpers = callbacks.getHelpers();
		stageId = new HashMap<String, String>();
		if (reportAPI == null) err.println("Error occured.");
	}

	@Override
	public void processHttpMessage(int toolFlag, boolean messageIsRequest, IHttpRequestResponse messageInfo) {
		IRequestInfo info = helpers.analyzeRequest(messageInfo);
		List<String> headers = info.getHeaders();
		int c = 0;
		String uid = null;
		boolean isStart = false;
		for (String header: headers) {
			if (header.toLowerCase().equals("host: ak-gs.hypergryph.com:8443")) ++c;
			else if (header.startsWith("POST /quest/battleStart ")) {
				++c;
				isStart = true;
			} else if (header.startsWith("POST /quest/battleFinish ")) {
				++c;
				isStart = false;
			} else if (header.startsWith("uid: ")) {
				++c;
				uid = header.substring(4).trim();
			}
		}
		if (c == 3) {
			String sid;
			if (isStart && messageIsRequest) {
				String content = new String(messageInfo.getRequest(), info.getBodyOffset(), messageInfo.getRequest().length - info.getBodyOffset());
				JsonObject obj = Json.parse(content).asObject();
				if (obj.get("usePracticeTicket").asInt() == 0) { // Isn't practice
					sid = obj.get("stageId").asString();
					stageId.put(uid, sid);
				}
			} else if (!isStart && !messageIsRequest && (sid = stageId.get(uid)) != null) {
				byte[] response = messageInfo.getResponse();
				IResponseInfo resinfo = helpers.analyzeResponse(response);
				String content = new String(response, resinfo.getBodyOffset(), response.length - resinfo.getBodyOffset());
				JsonObject obj = Json.parse(content).asObject();
				JsonObject post = Json.object().add("stageId", sid);
				JsonArray drops = Json.array();
				stageId.remove(uid);
				JsonValue[] dropGroups = {obj.get("rewards"), obj.get("additionalRewards"), obj.get("unusualRewards")}; // Shouldn't collect firstRewards
				for (JsonValue dropGroup: dropGroups) {
					JsonArray group = dropGroup.asArray();
					for (JsonValue value: group) {
						JsonObject item = value.asObject();
						String type = item.get("type").asString();
						if (type.equals("MATERIAL") || type.equals("CARD_EXP")) {
							int count = item.get("count").asInt();
							if (count > 0) drops.add(Json.object().add("itemId", item.get("id").asString()).add("quantity", count));
						}
					}
				}
				int furniCount = 0;
				for (JsonValue value: obj.get("furnitureRewards").asArray()) {
					JsonObject item = value.asObject();
					furniCount += item.get("count").asInt();
				}
				post.add("furnitureNum", furniCount).add("drops", drops);
				String postData = post.toString();
				out.println(postData);
				try {
					HttpURLConnection conn = (HttpURLConnection)reportAPI.openConnection();
					conn.setRequestMethod("POST");
					conn.setDoOutput(true);
					conn.setRequestProperty("Content-Type", "application/json");
					conn.connect();
					OutputStream os = conn.getOutputStream();
					os.write(postData.getBytes("UTF-8"));
					os.flush();
					os.close();
					if (conn.getResponseCode() == 200) out.println("Upload successed.");
					else out.println("Upload failed.");
				}
				catch(IOException e) {
					out.println("Upload failed.");
				}
			}
		}
	}

	static {
		try {
			reportAPI = new URL("https://penguin-stats.io/PenguinStats/api/report");
		}
		catch(MalformedURLException e) {
			e.printStackTrace();
		}
	}
}
