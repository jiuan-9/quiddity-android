package com.quiddity.app.domain.agent

import kotlin.test.Test
import kotlin.test.assertEquals

class AgentSetupRouterTest {

    @Test
    fun belowMinSdk_routesUnsupported() {
        assertEquals(AgentSetupPath.Unsupported, AgentSetupRouter.routeFor(25))
        assertEquals(AgentSetupPath.Unsupported, AgentSetupRouter.routeFor(0))
    }

    @Test
    fun android8to10_routesPcTool() {
        assertEquals(AgentSetupPath.PcTool, AgentSetupRouter.routeFor(26))
        assertEquals(AgentSetupPath.PcTool, AgentSetupRouter.routeFor(28))
        assertEquals(AgentSetupPath.PcTool, AgentSetupRouter.routeFor(30))
    }

    @Test
    fun android11Plus_routesWirelessDebugging() {
        assertEquals(AgentSetupPath.WirelessDebugging, AgentSetupRouter.routeFor(31))
        assertEquals(AgentSetupPath.WirelessDebugging, AgentSetupRouter.routeFor(34))
        assertEquals(AgentSetupPath.WirelessDebugging, AgentSetupRouter.routeFor(99))
    }

    @Test
    fun routeForCurrent_usesProvidedSdk() {
        assertEquals(AgentSetupPath.WirelessDebugging, AgentSetupRouter.routeForCurrent { 33 })
        assertEquals(AgentSetupPath.PcTool, AgentSetupRouter.routeForCurrent { 29 })
    }
}
