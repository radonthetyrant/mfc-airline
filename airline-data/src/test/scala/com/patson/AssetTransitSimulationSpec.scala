package com.patson

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.patson.PassengerSimulation.RouteRejectionReason
import com.patson.model.FlightType._
import com.patson.model._
import com.patson.model.airplane.AirplaneMaintenanceUtil
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import java.util.Collections
import scala.collection.mutable.Set
import scala.jdk.CollectionConverters._

class AssetTransitSimulationSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("MySpec"))

  override protected def beforeAll() : Unit = {
    super.beforeAll()
    AirplaneMaintenanceUtil.setTestFactor(Some(1))
  }

  override protected def afterAll() : Unit = {
    AirplaneMaintenanceUtil.setTestFactor(None)
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  val testAirline1 = Airline("airline 1", id = 1)
  val testAirline2 = Airline("airline 2", id = 2)


  val fromAirport = Airport.fromId(1).copy(baseIncome = 40000, basePopulation = 1) //income 40k . mid income country
  val airlineAppeal = AirlineAppeal(0)
  fromAirport.initAirlineAppeals(Map(testAirline1.id -> airlineAppeal, testAirline2.id -> airlineAppeal))
  fromAirport.initLounges(List.empty)
  val toAirportsList = List(
    Airport("", "", "To Airport", 0, 30, "", "", "", 1, 0, 0, 0, id = 2),
    Airport("", "", "To Airport", 0, 60, "", "", "", 1, 0, 0, 0, id = 3),
    Airport("", "", "To Airport", 0, 90, "", "", "", 1, 0, 0, 0, id = 4))


  toAirportsList.foreach { airport =>
    airport.initAirlineAppeals(Map(testAirline1.id -> airlineAppeal, testAirline2.id -> airlineAppeal))
    airport.initLounges(List.empty)
  }
  val toAirports = Set(toAirportsList : _*)

  val allAirportIds = Set[Int]()
  allAirportIds ++= toAirports.map {
    _.id
  }
  allAirportIds += fromAirport.id
  val LOOP_COUNT = 10000


  def assignLinkIds(links : List[Link]) = {
    var id = 1
    links.foreach { link =>
      if (link.id == 0) {
        link.id = id
      }
      id += 1
    }
  }


  val iterations = 10000
  //  val airline1Link = Link(fromAirport, toAirport, testAirline1, 100, 10000, 10000, 0, 600, 1)
  //  val airline2Link = Link(fromAirport, toAirport, testAirline2, 100, 10000, 10000, 0, 600, 1)
  val passengerGroup = PassengerGroup(fromAirport, SimplePreference(homeAirport = fromAirport, priceSensitivity = 1, preferredLinkClass = ECONOMY), PassengerType.BUSINESS)
  //def findShortestRoute(from : Airport, toAirports : Set[Airport], allVertices: Set[Airport], linksWithCost : List[LinkWithCost], maxHop : Int) : Map[Airport, Route] = {
  "Find routes by assets" should {

    "prefer routes with transit airport that has airport hotel (more so for premium pax)" in {
      val airport1 = Airport("", "", "Airport 1", 0, 30, "C1", "", "", 1, 0, 0, 0, id = 1)
      val airport2a = Airport("", "", "Airport 2a", 0, 60, "C1", "", "", 1, 0, 0, 0, id = 2)
      val airport2b = Airport("", "", "Airport 2b", 0, 60, "C1", "", "", 1, 0, 0, 0, id = 3)
      val airport3 = Airport("", "", "Airport 3", 0, 90, "C2", "", "", 1, 0, 0, 0, id = 4)


      val airline1 = Airline("airline 1", id = 1)
      airline1.setBases(List[AirlineBase](AirlineBase(airline1, airport1, "C1", 1, 1, headquarter = true)))

      airport1.initAirlineAppeals(Map(airline1.id -> AirlineAppeal(0)))

      airport2a.initAssets(List(AirportAsset.getAirportAsset(AirportAssetBlueprint(airport2a, AirportAssetType.AIRPORT_HOTEL, 0), Some(airline1), "Test Hotel", 10, Some(0), List.empty, 0, 0, 0, true, Map.empty, 0)))
      airport2b.initAssets(List.empty)

      val countryOpenness = Map[String, Int](
        "C1" -> 10,
        "C2" -> 10)


      val links = List(
        Link(airport1, airport2a, airline1, price = Pricing.computeStandardPriceForAllClass(2000, SHORT_HAUL_DOMESTIC), 2000, capacity = LinkClassValues.getInstance(10000, 10000, 10000), 0, 600, 7, SHORT_HAUL_DOMESTIC),
        Link(airport1, airport2b, airline1, price = Pricing.computeStandardPriceForAllClass(2000, SHORT_HAUL_DOMESTIC), 2000, capacity = LinkClassValues.getInstance(10000, 10000, 10000), 0, 600, 7, SHORT_HAUL_DOMESTIC),
        Link(airport2a, airport3, airline1, price = Pricing.computeStandardPriceForAllClass(2000, SHORT_HAUL_DOMESTIC), 2000, capacity = LinkClassValues.getInstance(10000, 10000, 10000), 0, 600, 7, SHORT_HAUL_DOMESTIC),
        Link(airport2b, airport3, airline1, price = Pricing.computeStandardPriceForAllClass(2000, SHORT_HAUL_DOMESTIC), 2000, capacity = LinkClassValues.getInstance(10000, 10000, 10000), 0, 600, 7, SHORT_HAUL_DOMESTIC),
      )

      assignLinkIds(links)
      val economyPassengerGroup = PassengerGroup(airport1, AppealPreference(airport1, ECONOMY, loungeLevelRequired = 0, loyaltyRatio = 1, 0), PassengerType.BUSINESS)
      val businessPassengerGroup = PassengerGroup(airport1, AppealPreference(airport1, BUSINESS, loungeLevelRequired = 0, loyaltyRatio = 1, 0), PassengerType.BUSINESS)
      val firstPassengerGroup = PassengerGroup(airport1, AppealPreference(airport1, FIRST, loungeLevelRequired = 0, loyaltyRatio = 1, 0), PassengerType.BUSINESS)

      val toAirports = Set[Airport](airport3)

      val activeAirports = links.flatMap(link => Set(link.from.id, link.to.id)).to(collection.mutable.Set)

      var economyViaHotel = 0
      var businessViaHotel = 0
      var firstViaHotel = 0

      for (i <- 0 until iterations) {
        val result : Map[PassengerGroup, Map[Airport, Route]] =
          PassengerSimulation.findAllRoutes(
            Map(economyPassengerGroup -> toAirports,
              businessPassengerGroup -> toAirports,
              firstPassengerGroup -> toAirports),
            links,
            activeAirports,
            countryOpenness = countryOpenness)

        if (result(economyPassengerGroup)(airport3).links(0).link.to == airport2a) {
          economyViaHotel += 1
        }
        if (result(businessPassengerGroup)(airport3).links(0).link.to == airport2a) {
          businessViaHotel += 1
        }
        if (result(businessPassengerGroup)(airport3).links(0).link.to == airport2a) {
          firstViaHotel += 1
        }
      }

      assert(economyViaHotel.toDouble / iterations > 0.5)
      assert(economyViaHotel.toDouble / iterations < 0.7)

      assert(businessViaHotel.toDouble / iterations > 0.6)
      assert(businessViaHotel.toDouble / iterations < 0.8)

      assert(firstViaHotel.toDouble / iterations > 0.6)
      assert(firstViaHotel.toDouble / iterations < 0.8)
    }

    "stopover asset has no effect on SWIFT pax" in {
      val airport1 = Airport("", "", "Airport 1", 0, 30, "C1", "", "", 1, 0, 0, 0, id = 1)
      val airport2a = Airport("", "", "Airport 2a", 0, 60, "C1", "", "", 1, 0, 0, 0, id = 2)
      val airport2b = Airport("", "", "Airport 2b", 0, 60, "C1", "", "", 1, 0, 0, 0, id = 3)
      val airport3 = Airport("", "", "Airport 3", 0, 90, "C2", "", "", 1, 0, 0, 0, id = 4)


      val airline1 = Airline("airline 1", id = 1)
      airline1.setBases(List[AirlineBase](AirlineBase(airline1, airport1, "C1", 1, 1, headquarter = true)))

      airport1.initAirlineAppeals(Map(airline1.id -> AirlineAppeal(0)))

      airport2a.initAssets(List(AirportAsset.getAirportAsset(AirportAssetBlueprint(airport2a, AirportAssetType.AMUSEMENT_PARK, 0), Some(airline1), "Test Asset", 10, Some(0), List.empty, 0, 0, 0, true, Map.empty, 0)))
      airport2b.initAssets(List.empty)

      val countryOpenness = Map[String, Int](
        "C1" -> 10,
        "C2" -> 10)


      val links = List(
        Link(airport1, airport2a, airline1, price = Pricing.computeStandardPriceForAllClass(2000, SHORT_HAUL_DOMESTIC), 2000, capacity = LinkClassValues.getInstance(10000, 10000, 10000), 0, 600, 7, SHORT_HAUL_DOMESTIC),
        Link(airport1, airport2b, airline1, price = Pricing.computeStandardPriceForAllClass(2000, SHORT_HAUL_DOMESTIC), 2000, capacity = LinkClassValues.getInstance(10000, 10000, 10000), 0, 600, 7, SHORT_HAUL_DOMESTIC),
        Link(airport2a, airport3, airline1, price = Pricing.computeStandardPriceForAllClass(2000, SHORT_HAUL_DOMESTIC), 2000, capacity = LinkClassValues.getInstance(10000, 10000, 10000), 0, 600, 7, SHORT_HAUL_DOMESTIC),
        Link(airport2b, airport3, airline1, price = Pricing.computeStandardPriceForAllClass(2000, SHORT_HAUL_DOMESTIC), 2000, capacity = LinkClassValues.getInstance(10000, 10000, 10000), 0, 600, 7, SHORT_HAUL_DOMESTIC),
      )

      assignLinkIds(links)
      val economyPassengerGroup = PassengerGroup(airport1, SpeedPreference(airport1, ECONOMY), PassengerType.BUSINESS)
      val businessPassengerGroup = PassengerGroup(airport1, SpeedPreference(airport1, BUSINESS), PassengerType.BUSINESS)

      val toAirports = Set[Airport](airport3)

      val activeAirports = links.flatMap(link => Set(link.from.id, link.to.id)).to(collection.mutable.Set)

      var economyViaHotel = 0
      var businessViaHotel = 0
      var firstViaHotel = 0

      for (i <- 0 until iterations) {
        val result : Map[PassengerGroup, Map[Airport, Route]] =
          PassengerSimulation.findAllRoutes(
            Map(economyPassengerGroup -> toAirports,
              businessPassengerGroup -> toAirports),
            links,
            activeAirports,
            countryOpenness = countryOpenness)

        if (result(economyPassengerGroup)(airport3).links(0).link.to == airport2a) {
          economyViaHotel += 1
        }
        if (result(businessPassengerGroup)(airport3).links(0).link.to == airport2a) {
          businessViaHotel += 1
        }
        if (result(businessPassengerGroup)(airport3).links(0).link.to == airport2a) {
          firstViaHotel += 1
        }
      }

      assert(economyViaHotel.toDouble / iterations > 0.45)
      assert(economyViaHotel.toDouble / iterations < 0.55)

      assert(businessViaHotel.toDouble / iterations > 0.45)
      assert(businessViaHotel.toDouble / iterations < 0.55)
    }

    "asset should have similar effect to different pax link class" in {
      val airport1 = Airport("", "", "Airport 1", 0, 30, "C1", "", "", 1, 0, 0, 0, id = 1)
      val airport2a = Airport("", "", "Airport 2a", 0, 60, "C1", "", "", 1, 0, 0, 0, id = 2)
      val airport2b = Airport("", "", "Airport 2b", 0, 60, "C1", "", "", 1, 0, 0, 0, id = 3)
      val airport3 = Airport("", "", "Airport 3", 0, 90, "C2", "", "", 1, 0, 0, 0, id = 4)


      val airline1 = Airline("airline 1", id = 1)
      airline1.setBases(List[AirlineBase](AirlineBase(airline1, airport1, "C1", 1, 1, headquarter = true)))

      airport1.initAirlineAppeals(Map(airline1.id -> AirlineAppeal(0)))

      airport2a.initAssets(List(AirportAsset.getAirportAsset(AirportAssetBlueprint(airport2a, AirportAssetType.CONVENTION_CENTER, 0), Some(airline1), "Test Asset", 10, Some(0), List.empty, 0, 0, 0, true, Map.empty, 0)))
      airport2b.initAssets(List.empty)

      val countryOpenness = Map[String, Int](
        "C1" -> 10,
        "C2" -> 10)


      val links = List(
        Link(airport1, airport2a, airline1, price = Pricing.computeStandardPriceForAllClass(8000, LONG_HAUL_INTERCONTINENTAL), 8000, capacity = LinkClassValues.getInstance(10000, 10000, 10000), 0, 600, 7, LONG_HAUL_INTERCONTINENTAL),
        Link(airport1, airport2b, airline1, price = Pricing.computeStandardPriceForAllClass(8000, LONG_HAUL_INTERCONTINENTAL), 8000, capacity = LinkClassValues.getInstance(10000, 10000, 10000), 0, 600, 7, LONG_HAUL_INTERCONTINENTAL),
        Link(airport2a, airport3, airline1, price = Pricing.computeStandardPriceForAllClass(8000, LONG_HAUL_INTERCONTINENTAL), 8000, capacity = LinkClassValues.getInstance(10000, 10000, 10000), 0, 600, 7, LONG_HAUL_INTERCONTINENTAL),
        Link(airport2b, airport3, airline1, price = Pricing.computeStandardPriceForAllClass(8000, LONG_HAUL_INTERCONTINENTAL), 8000, capacity = LinkClassValues.getInstance(10000, 10000, 10000), 0, 600, 7, LONG_HAUL_INTERCONTINENTAL),
      )

      assignLinkIds(links)
      val economyPassengerGroup = PassengerGroup(airport1, AppealPreference(airport1, ECONOMY, 0, 1, 1), PassengerType.BUSINESS)
      val businessPassengerGroup = PassengerGroup(airport1, AppealPreference(airport1, BUSINESS, 0, 1, 2), PassengerType.BUSINESS)
      val firstPassengerGroup = PassengerGroup(airport1, AppealPreference(airport1, FIRST, 0, 1, 3), PassengerType.BUSINESS)

      val toAirports = Set[Airport](airport3)

      val activeAirports = links.flatMap(link => Set(link.from.id, link.to.id)).to(collection.mutable.Set)

      var economyViaAsset = 0
      var businessViaAsset = 0
      var firstViaAsset = 0
      val iterations = 10000

      for (i <- 0 until iterations) {
        val result : Map[PassengerGroup, Map[Airport, Route]] =
          PassengerSimulation.findAllRoutes(
            Map(economyPassengerGroup -> toAirports,
              businessPassengerGroup -> toAirports,
              firstPassengerGroup -> toAirports),
            links,
            activeAirports,
            countryOpenness = countryOpenness)

        if (result(economyPassengerGroup)(airport3).links(0).link.to == airport2a) {
          economyViaAsset += 1
        }
        if (result(businessPassengerGroup)(airport3).links(0).link.to == airport2a) {
          businessViaAsset += 1
        }
        if (result(businessPassengerGroup)(airport3).links(0).link.to == airport2a) {
          firstViaAsset += 1
        }
      }

      assert(economyViaAsset.toDouble / iterations > 0.5)
      assert(economyViaAsset.toDouble / iterations < 0.6)

      assert(businessViaAsset.toDouble / iterations > 0.5)
      assert(businessViaAsset.toDouble / iterations < 0.6)

      assert(firstViaAsset.toDouble / iterations > 0.5)
      assert(firstViaAsset.toDouble / iterations < 0.6)

      println(economyViaAsset.toDouble / iterations)
      println(businessViaAsset.toDouble / iterations)
      println(firstViaAsset.toDouble / iterations)
    }

    "asset effect on long haul + short haul, should have stronger effect" in {
      val airport1 = Airport("", "", "Airport 1", 0, 30, "C1", "", "", 1, 0, 0, 0, id = 1)
      val airport2a = Airport("", "", "Airport 2a", 0, 60, "C2", "", "", 1, 0, 0, 0, id = 2)
      val airport2b = Airport("", "", "Airport 2b", 0, 60, "C2", "", "", 1, 0, 0, 0, id = 3)
      val airport3 = Airport("", "", "Airport 3", 0, 70, "C2", "", "", 1, 0, 0, 0, id = 4)


      val airline1 = Airline("airline 1", id = 1)
      airline1.setBases(List[AirlineBase](AirlineBase(airline1, airport1, "C1", 1, 1, headquarter = true)))

      airport1.initAirlineAppeals(Map(airline1.id -> AirlineAppeal(0)))

      airport2a.initAssets(List(AirportAsset.getAirportAsset(AirportAssetBlueprint(airport2a, AirportAssetType.CONVENTION_CENTER, 0), Some(airline1), "Test Asset", 10, Some(0), List.empty, 0, 0, 0, true, Map.empty, 0)))
      airport2b.initAssets(List.empty)

      val countryOpenness = Map[String, Int](
        "C1" -> 10,
        "C2" -> 10)


      val links = List(
        Link(airport1, airport2a, airline1, price = Pricing.computeStandardPriceForAllClass(8000, LONG_HAUL_INTERCONTINENTAL), 8000, capacity = LinkClassValues.getInstance(10000, 10000, 10000), 0, 600, 7, LONG_HAUL_INTERCONTINENTAL),
        Link(airport1, airport2b, airline1, price = Pricing.computeStandardPriceForAllClass(8000, LONG_HAUL_INTERCONTINENTAL), 8000, capacity = LinkClassValues.getInstance(10000, 10000, 10000), 0, 600, 7, LONG_HAUL_INTERCONTINENTAL),
        Link(airport2a, airport3, airline1, price = Pricing.computeStandardPriceForAllClass(500, SHORT_HAUL_DOMESTIC), 500, capacity = LinkClassValues.getInstance(10000, 10000, 10000), 0, 30, 7, SHORT_HAUL_DOMESTIC),
        Link(airport2b, airport3, airline1, price = Pricing.computeStandardPriceForAllClass(500, SHORT_HAUL_DOMESTIC), 500, capacity = LinkClassValues.getInstance(10000, 10000, 10000), 0, 30, 7, SHORT_HAUL_DOMESTIC),
      )

      assignLinkIds(links)
      val pool = DemandGenerator.getFlightPreferencePoolOnAirport(fromAirport)

      val toAirports = Set[Airport](airport3)

      val activeAirports = links.flatMap(link => Set(link.from.id, link.to.id)).to(collection.mutable.Set)

      var economyViaAsset = 0

      for (i <- 0 until iterations) {
        val economyPassengerGroup = PassengerGroup(airport1, pool.draw(ECONOMY, airport1, airport3), PassengerType.BUSINESS)
        val result : Map[PassengerGroup, Map[Airport, Route]] =
          PassengerSimulation.findAllRoutes(
            Map(economyPassengerGroup -> toAirports),
            links,
            activeAirports,
            countryOpenness = countryOpenness)

        if (result(economyPassengerGroup)(airport3).links(0).link.to == airport2a) {
          economyViaAsset += 1
        }
      }

      assert(economyViaAsset.toDouble / iterations > 0.57)
      assert(economyViaAsset.toDouble / iterations < 0.6)
    }

    "asset effect on short haul + long haul, should have weaker effect" in {
      val airport1 = Airport("", "", "Airport 1", 0, 30, "C1", "", "", 1, 0, 0, 0, id = 1)
      val airport2a = Airport("", "", "Airport 2a", 0, 40, "C1", "", "", 1, 0, 0, 0, id = 2)
      val airport2b = Airport("", "", "Airport 2b", 0, 40, "C1", "", "", 1, 0, 0, 0, id = 3)
      val airport3 = Airport("", "", "Airport 3", 0, 90, "C2", "", "", 1, 0, 0, 0, id = 4)


      val airline1 = Airline("airline 1", id = 1)
      airline1.setBases(List[AirlineBase](AirlineBase(airline1, airport1, "C1", 1, 1, headquarter = true)))

      airport1.initAirlineAppeals(Map(airline1.id -> AirlineAppeal(0)))

      airport2a.initAssets(List(AirportAsset.getAirportAsset(AirportAssetBlueprint(airport2a, AirportAssetType.CONVENTION_CENTER, 0), Some(airline1), "Test Asset", 10, Some(0), List.empty, 0, 0, 0, true, Map.empty, 0)))
      airport2b.initAssets(List.empty)

      val countryOpenness = Map[String, Int](
        "C1" -> 10,
        "C2" -> 10)


      val links = List(
        Link(airport1, airport2a, airline1, price = Pricing.computeStandardPriceForAllClass(500, SHORT_HAUL_DOMESTIC), 500, capacity = LinkClassValues.getInstance(10000, 10000, 10000), 0, 30, 7, SHORT_HAUL_DOMESTIC),
        Link(airport1, airport2b, airline1, price = Pricing.computeStandardPriceForAllClass(500, SHORT_HAUL_DOMESTIC), 500, capacity = LinkClassValues.getInstance(10000, 10000, 10000), 0, 30, 7, SHORT_HAUL_DOMESTIC),
        Link(airport2a, airport3, airline1, price = Pricing.computeStandardPriceForAllClass(8000, LONG_HAUL_INTERCONTINENTAL), 8000, capacity = LinkClassValues.getInstance(10000, 10000, 10000), 0, 600, 7, LONG_HAUL_INTERCONTINENTAL),
        Link(airport2b, airport3, airline1, price = Pricing.computeStandardPriceForAllClass(8000, LONG_HAUL_INTERCONTINENTAL), 8000, capacity = LinkClassValues.getInstance(10000, 10000, 10000), 0, 600, 7, LONG_HAUL_INTERCONTINENTAL),
      )

      assignLinkIds(links)
      val pool = DemandGenerator.getFlightPreferencePoolOnAirport(fromAirport)

      val toAirports = Set[Airport](airport3)

      val activeAirports = links.flatMap(link => Set(link.from.id, link.to.id)).to(collection.mutable.Set)

      var economyViaAsset = 0

      for (i <- 0 until iterations) {
        val economyPassengerGroup = PassengerGroup(airport1, pool.draw(ECONOMY, airport1, airport3), PassengerType.BUSINESS)
        val result : Map[PassengerGroup, Map[Airport, Route]] =
          PassengerSimulation.findAllRoutes(
            Map(economyPassengerGroup -> toAirports),
            links,
            activeAirports,
            countryOpenness = countryOpenness)

        if (result(economyPassengerGroup)(airport3).links(0).link.to == airport2a) {
          economyViaAsset += 1
        }
      }

      assert(economyViaAsset.toDouble / iterations > 0.5)
      assert(economyViaAsset.toDouble / iterations < 0.55)
    }

    "stopover asset effect on pax" when {
      "amusement park" in {
        assertAssetEffect(AirportAssetType.AMUSEMENT_PARK, EffectBoundary(0.54, 0.57, 0.49, 0.51))
      }
      "SkiResortAssetType" in {
        assertAssetEffect(AirportAssetType.SKI_RESORT, EffectBoundary(0.58, 0.61, 0.5, 0.52))
      }
      "BeachResortAssetType" in {
        assertAssetEffect(AirportAssetType.BEACH_RESORT, EffectBoundary(0.54, 0.57, 0.5, 0.52))
      }
      "ConventionCenterAssetType" in {
        assertAssetEffect(AirportAssetType.CONVENTION_CENTER, EffectBoundary(0.49, 0.51, 0.58, 0.61))
      }
      "MuseumAssetType" in {
        assertAssetEffect(AirportAssetType.MUSEUM, EffectBoundary(0.52, 0.54, 0.5, 0.52))
      }
      "StadiumAssetType" in {
        assertAssetEffect(AirportAssetType.STADIUM, EffectBoundary(0.51, 0.53, 0.5, 0.52))
      }
      "LandmarkAssetType" in {
        assertAssetEffect(AirportAssetType.LANDMARK, EffectBoundary(0.53, 0.56, 0.53, 0.56))
      }
      "GolfCourseAssetType" in {
        assertAssetEffect(AirportAssetType.GOLF_COURSE, EffectBoundary(0.5, 0.53, 0.5, 0.53))
      }
    }
  }










  case class EffectBoundary(touristLowerBound : Double, touristUpperBound : Double, businessLowerBound : Double, businessUpperBound : Double)

  def assertAssetEffect(assetType : AirportAssetType.Value, effectBoundaries : EffectBoundary): Unit = {
    val airport1 = Airport("", "", "Airport 1", 0, 30, "C1", "", "", 1, 0, 0, 0, id = 1)
    val airport2a = Airport("", "", "Airport 2a", 0, 60, "C1", "", "", 1, 0, 0, 0, id = 2)
    val airport2b = Airport("", "", "Airport 2b", 0, 60, "C1", "", "", 1, 0, 0, 0, id = 3)
    val airport3 = Airport("", "", "Airport 3", 0, 90, "C2", "", "", 1, 0, 0, 0, id = 4)


    val airline1 = Airline("airline 1", id = 1)
    airline1.setBases(List[AirlineBase](AirlineBase(airline1, airport1, "C1", 1, 1, headquarter = true)))

    airport1.initAirlineAppeals(Map(airline1.id -> AirlineAppeal(0)))

    airport2a.initAssets(List(AirportAsset.getAirportAsset(AirportAssetBlueprint(airport2a, assetType, 0), Some(airline1), "Test Asset", 10, Some(0), List.empty, 0, 0, 0, true, Map.empty, 0)))
    airport2b.initAssets(List.empty)

    val countryOpenness = Map[String, Int](
      "C1" -> 10,
      "C2" -> 10)


    val links = List(
      Link(airport1, airport2a, airline1, price = Pricing.computeStandardPriceForAllClass(8000, LONG_HAUL_INTERCONTINENTAL), 8000, capacity = LinkClassValues.getInstance(10000, 10000, 10000), 0, 600, 7, LONG_HAUL_INTERCONTINENTAL),
      Link(airport1, airport2b, airline1, price = Pricing.computeStandardPriceForAllClass(8000, LONG_HAUL_INTERCONTINENTAL), 8000, capacity = LinkClassValues.getInstance(10000, 10000, 10000), 0, 600, 7, LONG_HAUL_INTERCONTINENTAL),
      Link(airport2a, airport3, airline1, price = Pricing.computeStandardPriceForAllClass(500, SHORT_HAUL_DOMESTIC), 500, capacity = LinkClassValues.getInstance(10000, 10000, 10000), 0, 30, 7, SHORT_HAUL_DOMESTIC),
      Link(airport2b, airport3, airline1, price = Pricing.computeStandardPriceForAllClass(500, SHORT_HAUL_DOMESTIC), 500, capacity = LinkClassValues.getInstance(10000, 10000, 10000), 0, 30, 7, SHORT_HAUL_DOMESTIC),
    )

    assignLinkIds(links)


    val toAirports = Set[Airport](airport3)

    val activeAirports = links.flatMap(link => Set(link.from.id, link.to.id)).to(collection.mutable.Set)

    var touristViaAsset = 0
    var businessViaAsset = 0

    val pool = DemandGenerator.getFlightPreferencePoolOnAirport(fromAirport)

    for (i <- 0 until iterations) {
      val preference = pool.draw(ECONOMY, airport1, airport3)
      val touristPassengerGroup = PassengerGroup(airport1, preference, PassengerType.TOURIST)
      val businessPassengerGroup = PassengerGroup(airport1, preference, PassengerType.BUSINESS)

      val result : Map[PassengerGroup, Map[Airport, Route]] =
        PassengerSimulation.findAllRoutes(
          Map(touristPassengerGroup -> toAirports,
            businessPassengerGroup -> toAirports),
          links,
          activeAirports,
          countryOpenness = countryOpenness)

      if (result(touristPassengerGroup)(airport3).links(0).link.to == airport2a) {
        touristViaAsset += 1
      }
      if (result(businessPassengerGroup)(airport3).links(0).link.to == airport2a) {
        businessViaAsset += 1
      }
    }

    assert(touristViaAsset.toDouble / iterations > effectBoundaries.touristLowerBound)
    assert(touristViaAsset.toDouble / iterations < effectBoundaries.touristUpperBound)

    assert(businessViaAsset.toDouble / iterations > effectBoundaries.businessLowerBound)
    assert(businessViaAsset.toDouble / iterations < effectBoundaries.businessUpperBound)
  }
}


