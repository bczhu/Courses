############################################################
##    FILENAME:   justDoItAgents.py    
##    VERSION:    1.0
##    SINCE:      2014-03-28
##    AUTHOR: 
##        Jimmy Lin (xl5224) - JimmyLin@utexas.edu
##	      Aming Ni(an23366) - amingniutcs@gmail.com
##
############################################################
##    Edited by MacVim
##    Documentation auto-generated by Snippet 
############################################################


import distanceCalculator
import capture
import random
from captureAgents import CaptureAgent
from captureAgents import AgentFactory

class JustDoItAgents (AgentFactory):
  def __init__(self, isRed, first='offense', second='defense', rest='offense'):
      AgentFactory.__init__(self, isRed)
      self.agents = [first, second]
      self.rest = rest

  def getAgent(self, index):
      if len(self.agents) > 0:
          return self.choose(self.agents.pop(0), index)
      else:
          return self.choose(self.rest, index)

  def choose(self, agentStr, index):
      if agentStr == 'keys':
          global NUM_KEYBOARD_AGENTS
          NUM_KEYBOARD_AGENTS += 1
          if NUM_KEYBOARD_AGENTS == 1:
              return keyboardAgents.KeyboardAgent(index)
          elif NUM_KEYBOARD_AGENTS == 2:
              return keyboardAgents.KeyboardAgent2(index)
          else:
              raise Exception('Max of two keyboard agents supported')
      elif agentStr == 'offense':
          return DiabloSlashAgentOne(index)
      elif agentStr == 'defense':
          return DiabloSlashAgentTwo(index)
      else:
          raise Exception("No staff agent identified by " + agentStr)


class DiabloSlashAgentOne (CaptureAgent):
    def __init__(self, index):
        CaptureAgent.__init__(self, index)

    def chooseAction(self,gameState):
        return random.choice( gameState.getLegalActions( self.index ) )

class DiabloSlashAgentTwo (CaptureAgent):
    def __init__(self, index):
        CaptureAgent.__init__(self, index)

    def chooseAction(self,gameState):
        return random.choice( gameState.getLegalActions( self.index) )
