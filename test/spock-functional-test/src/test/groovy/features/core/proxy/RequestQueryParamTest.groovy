package features.core.proxy

import framework.ReposeValveTest
import org.rackspace.deproxy.Deproxy
import org.rackspace.deproxy.MessageChain
import spock.lang.Unroll

class RequestQueryParamTest extends ReposeValveTest {

    def setupSpec() {

        deproxy = new Deproxy()
        deproxy.addEndpoint(properties.getProperty("target.port").toInteger())

        repose.applyConfigs( "features/core/proxy" )
        repose.start()

    }

    @Unroll("When client requests: #method #uriSuffixGiven, repose should normalize to: #uriSuffixExpected")
    def "when given a query param list, Repose should forward a valid query param list"() {

        when: "the client makes a request through Repose"
        MessageChain messageChain = deproxy.makeRequest(url: reposeEndpoint + uriSuffixGiven, method: method)


        then: "after passing through Repose, request path should contain a valid query param list"
        messageChain.handlings.size() == 1
        messageChain.handlings[0].request.path.endsWith(uriSuffixExpected)


        where: "given a path with query params defined"
        uriSuffixGiven                       | uriSuffixExpected                   | method
        "/path/to/resource?"                 | "/path/to/resource"                 | "GET"
        "/path/to/resource?"                 | "/path/to/resource"                 | "POST"
        "/path/to/resource?="                | "/path/to/resource"                 | "GET"
        "/path/to/resource?="                | "/path/to/resource"                 | "POST"
        "/path/to/resource?&"                | "/path/to/resource"                 | "GET"
        "/path/to/resource?&"                | "/path/to/resource"                 | "POST"
        "/path/to/resource?=&"               | "/path/to/resource"                 | "GET"
        "/path/to/resource?=&"               | "/path/to/resource"                 | "POST"
        "/path/to/resource?=&="              | "/path/to/resource"                 | "GET"
        "/path/to/resource?=&="              | "/path/to/resource"                 | "POST"
        "/path/to/resource?&=&"              | "/path/to/resource"                 | "GET"
        "/path/to/resource?&=&"              | "/path/to/resource"                 | "POST"
        "/path/to/resource?a=12345"          | "/path/to/resource?a=12345"         | "GET"
        "/path/to/resource?a=12345"          | "/path/to/resource?a=12345"         | "POST"
        "/path/to/resource?&a=12345"         | "/path/to/resource?a=12345"         | "GET"
        "/path/to/resource?&a=12345"         | "/path/to/resource?a=12345"         | "POST"
        "/path/to/resource/?&a=12345"        | "/path/to/resource/?a=12345"        | "GET"
        "/path/to/resource/?&a=12345"        | "/path/to/resource/?a=12345"        | "POST"
        "/path/to/resource?&a=12345&b=54321" | "/path/to/resource?a=12345&b=54321" | "GET"
        "/path/to/resource?&a=12345&b=54321" | "/path/to/resource?a=12345&b=54321" | "POST"
    }

    def "when given an improperly encoded URI character, Repose should pass it through"() {

        when:
        MessageChain messageChain = deproxy.makeRequest(reposeEndpoint + uriSuffixGiven, method)

        then:
        messageChain.handlings.size() == 1
        messageChain.handlings[0].request.path.endsWith(uriSuffixExpected)

        where:
        uriSuffixGiven                  | uriSuffixExpected               | method
        "/path/to/resource?key=value@%" | "/path/to/resource?key=value@%" | "GET"
    }

    def cleanupSpec() {

        if (repose) {
            repose.stop()
        }
        if (deproxy) {
            deproxy.shutdown()
        }
    }
}
