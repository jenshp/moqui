/*
 * This software is in the public domain under CC0 1.0 Universal plus a
 * Grant of Patent License.
 *
 * To the extent possible under law, the author(s) have dedicated all
 * copyright and related and neighboring rights to this software to the
 * public domain worldwide. This software is distributed without any
 * warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication
 * along with this software (see the LICENSE.md file). If not, see
 * <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

import org.moqui.Moqui
import org.moqui.context.ExecutionContext
import org.moqui.screen.ScreenTest
import org.moqui.screen.ScreenTest.ScreenTestRender
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class ToolsScreenRenderTests extends Specification {
    protected final static Logger logger = LoggerFactory.getLogger(ToolsScreenRenderTests.class)

    @Shared
    ExecutionContext ec
    @Shared
    ScreenTest screenTest

    def setupSpec() {
        ec = Moqui.getExecutionContext()
        ec.user.loginUser("john.doe", "moqui", null)
        screenTest = ec.screen.makeTest().baseScreenPath("apps/tools")
    }

    def cleanupSpec() {
        long totalTime = System.currentTimeMillis() - screenTest.startTime
        logger.info("Rendered ${screenTest.renderCount} screens (${screenTest.errorCount} errors) in ${ec.l10n.format(totalTime/1000, "0.000")}s, output ${ec.l10n.format(screenTest.renderTotalChars/1000, "#,##0")}k chars")

        ec.destroy()
    }

    def setup() {
        ec.artifactExecution.disableAuthz()
    }

    def cleanup() {
        ec.artifactExecution.enableAuthz()
    }

    @Unroll
    def "render tools screen #screenPath (#containsText1, #containsText2)"() {
        expect:
        ScreenTestRender str = screenTest.render(screenPath, null, null)
        // logger.info("Rendered ${screenPath} in ${str.getRenderTime()}ms")
        boolean contains1 = containsText1 ? str.assertContains(containsText1) : true
        boolean contains2 = containsText2 ? str.assertContains(containsText2) : true
        if (!contains1) logger.info("In ${screenPath} text 1 [${containsText1}] not found:\n${str.output}")
        if (!contains2) logger.info("In ${screenPath} text 2 [${containsText2}] not found:\n${str.output}")
        // assertions
        !str.errorMessages
        contains1
        contains2

        where:
        screenPath | containsText1 | containsText2
        "dashboard" | "" | ""

        // AutoScreen screens
        "AutoScreen/MainEntityList" | "" | ""
        "AutoScreen/AutoFind?aen=example.Example" | "Test Example Name" | "In Design [EXST_IN_DESIGN]"
        "AutoScreen/AutoEdit/AutoEditMaster?exampleId=TEST1&aen=example.Example" |
                "Test Example Name" | "example1@test.com"
        "AutoScreen/AutoEdit/AutoEditDetail?exampleId=TEST1&aen=example.Example&den=example.ExampleItem" |
                "Amount Uom ID" | "Test 1 Item 1"
        // test example.Example create through transition, then view it
        "AutoScreen/AutoFind/create?aen=example.Example&exampleId=TEST_SCR&exampleName=Screen Test Example&exampleTypeEnumId=EXT_MADE_UP&statusId=EXST_IN_DESIGN" | "" | ""
        "AutoScreen/AutoEdit/AutoEditMaster?exampleId=TEST_SCR&aen=example.Example" | "Screen Test Example" | ""

        // ArtifactStats screen
        // don't run, takes too long: "ArtifactStats" | "" | ""

        // DataView screens
        // see "render DataView screens"

        // Entity/DataEdit screens
        "Entity/DataEdit/EntityList" | "ExampleContent" | "example"
        "Entity/DataEdit/EntityDetail?entityName=example.Example" | "text-medium" | "moqui.basic.Enumeration"
        "Entity/DataEdit/EntityDataFind?entityName=example.Example" | "Screen Test Example" | "In Design"
        "Entity/DataEdit/EntityDataEdit?exampleId=TEST1&entityName=example.Example" |
                "Test description, with a comma" | "example1@test.com"
        "Entity/DataEdit/EntityDataFind?exampleId=TEST1&entityName=example.ExampleItem" |
                "Test 1 Item 1" | "exampleItemSeqId"

        // Other Entity screens
        "Entity/DataExport" | "example.Example" | ""
        // TODO: support response object with Writer in ScreenTestImpl/WebFacadeStub
        // "Entity/DataExport/EntityExport?entityNames=example.Example&dependentLevels=1&fileType=JSON&output=browser" | "Test Example Name" | "exampleItemSeqId"
        "Entity/DataImport" | "" | ""
        "Entity/SqlRunner?groupName=transactional&sql=SELECT * FROM EXAMPLE" | "Test Example Name" | "EXT_MADE_UP"
        // run with very few baseCalls so it doesn't take too long
        "Entity/SpeedTest?baseCalls=10" | "" | ""

        // Service screens
        "Service/ServiceReference?serviceName=example" |
                "org.moqui.example.ExampleServices.create#ExampleItem" | "Service Detail"
        "Service/ServiceDetail?serviceName=org.moqui.example.ExampleServices.consume#ExampleMessage" |
                "moqui.service.message.SystemMessage" | """ec.service.sync().name("store#example.Example")"""
        "Service/ServiceRun?serviceName=org.moqui.example.ExampleServices.create#ExampleItem" |
                "Example ID" | "Cron String"
        // run the service, then make sure it ran
        "Service/ServiceRun/run?serviceName=org.moqui.example.ExampleServices.create#ExampleItem&exampleId=TEST_SCR&description=ServiceRun Screen Test Item" | "" | ""
        "Entity/DataEdit/EntityDataFind?exampleId=TEST_SCR&entityName=example.ExampleItem" |
                "ServiceRun Screen Test Item" | ""
    }

    def "render DataView screens"() {
        // create a DbViewEntity, set MASTER and fields, view it
        when:
        ScreenTestRender createStr = screenTest.render("DataView/FindDbView/create",
                [dbViewEntityName:'ExampleDbView', packageName:'example', isDataView:'Y'], null)
        logger.info("Called FindDbView/create in ${createStr.getRenderTime()}ms")

        ScreenTestRender fdvStr = screenTest.render("DataView/FindDbView", null, null)
        logger.info("Rendered DataView/FindDbView in ${fdvStr.getRenderTime()}ms, ${fdvStr.output?.length()} characters")

        ScreenTestRender setMeStr = screenTest.render("DataView/EditDbView/setMasterEntity",
                [dbViewEntityName:'ExampleDbView', entityAlias:'MASTER', entityName:'example.Example'], null)
        logger.info("Called EditDbView/setMasterEntity in ${setMeStr.getRenderTime()}ms")

        ScreenTestRender setMfStr = screenTest.render("DataView/EditDbView/setMasterFields",
                [dbViewEntityName_0:'ExampleDbView', field_0:'example.Example.exampleName',
                 dbViewEntityName_1:'ExampleDbView', field_1:'Example#moqui.basic.StatusItem.description',
                 dbViewEntityName_2:'ExampleDbView', field_2:'ExampleType#moqui.basic.Enumeration.description'], null)
        logger.info("Called EditDbView/setMasterFields in ${setMfStr.getRenderTime()}ms")

        ScreenTestRender vdvStr = screenTest.render("DataView/ViewDbView?dbViewEntityName=ExampleDbView", null, null)
        logger.info("Rendered DataView/FindDbView in ${vdvStr.getRenderTime()}ms, ${vdvStr.output?.length()} characters")

        then:
        !createStr.errorMessages
        !fdvStr.errorMessages
        fdvStr.assertContains("ExampleDbView")
        !setMeStr.errorMessages
        !setMfStr.errorMessages
        !vdvStr.errorMessages
        vdvStr.assertContains("Screen Test Example")
        vdvStr.assertContains("In Design")
    }
}