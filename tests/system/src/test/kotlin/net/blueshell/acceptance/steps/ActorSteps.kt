package net.blueshell.acceptance.steps

import io.cucumber.java.en.Given
import net.blueshell.acceptance.AcceptanceWorld
import net.blueshell.systemtests.TestHelper

/**
 * Who a scenario acts as, where more than one feature needs the same person signed in.
 * An actor only one feature ever asks for stays with that feature's steps.
 */
class ActorSteps(private val world: AcceptanceWorld) {

    @Given("a board member signed in to the user manager")
    fun aBoardMemberSignedInToTheUserManager() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        world.createdUsernames += board.username
        world.authCookies = TestHelper.login(board)
    }
}
