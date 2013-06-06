package couch

import com.excilys.ebi.gatling.core.Predef._
import com.excilys.ebi.gatling.http.Predef._
import com.excilys.ebi.gatling.jdbc.Predef._
import com.excilys.ebi.gatling.http.Headers.Names._
import akka.util.duration._
import bootstrap._

class CouchSimulation extends Simulation {

	val httpConf = httpConfig
		.baseURL("http://localhost:9000/api")
		.acceptCharsetHeader("ISO-8859-1,utf-8;q=0.7,*;q=0.7")
		.acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
		.acceptEncodingHeader("gzip, deflate")
		.acceptLanguageHeader("fr,fr-fr;q=0.8,en-us;q=0.5,en;q=0.3")
		.disableFollowRedirect

	val scn = scenario("Test couchbase plugin").repeat(100) {
		exec(http("request_1").get("/urls")).pause(0 milliseconds, 1 milliseconds).
		exec(http("request_2").get("/urls/103")).pause(0 milliseconds, 1 milliseconds)
	}
	setUp(scn.users(1000).ramp(1).protocolConfig(httpConf))
}
