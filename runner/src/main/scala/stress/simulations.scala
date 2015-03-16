package stress

import io.gatling.core.Predef._
import stress.chain.Release
import stress.utils.Scenarios
import stress.utils.Scenarios._

import scala.language.postfixOps

/**
 * X users open calendar page on a given month
 */
class CalendarSimulation extends SimulationBase(openCalendarScenario)

/**
 * X concurrent users create releases (without template and tasks) simultaneously and get task definitions.
 */
class CreateReleaseSimulation extends SimulationBase(
  Scenarios.createReleaseScenario
)

/**
 * X concurrent users are opening tasks overview page
 */
class TasksOverviewSimulation extends SimulationBase(queryMyTasksScenario)

/**
 * X users are looking at release overview
 */
class ReleasesOverviewSimulation extends SimulationBase(
  scenario("Release overview").exec(Release.queryAll)
)

/**
 * X users are looking at the details of the release at Release Flow screen
 */
class ReleaseFlowSimulation extends SimulationBase(releaseFlowScenario)

/**
 * X release managers are working with XL Release
 */
class ReleaseManagerSimulation extends SimulationBase(releaseManagerScenario)

//class GroupSimulation extends Simulation {
//
//  setUp(
//      createReleaseScenario.inject(atOnceUsers(nbUsers)),
//      queryAllReleasesScenario.inject(atOnceUsers(nbUsers)),
//      queryPipelinesScenario.inject(atOnceUsers(nbUsers)),
//      openCalendarScenario.inject(atOnceUsers(nbUsers)),
//      queryMyTasksScenario.inject(atOnceUsers(nbUsers)),
//      queryTemplatesScenario.inject(atOnceUsers(nbUsers))
//    ).protocols(httpProtocol)
//
//}
