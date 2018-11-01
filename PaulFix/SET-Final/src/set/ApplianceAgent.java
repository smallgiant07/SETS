package set;

import jade.core.Agent;
import jade.core.behaviours.ParallelBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.lang.acl.MessageTemplate;

import java.util.Date;
import java.util.Vector;

import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.proto.AchieveREInitiator;
import jade.proto.AchieveREResponder;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.FailureException;
import jade.domain.FIPAAgentManagement.NotUnderstoodException;
import jade.domain.FIPAAgentManagement.RefuseException;
import jade.domain.FIPAAgentManagement.ServiceDescription;

@SuppressWarnings("serial")
public class ApplianceAgent extends Agent
{
	private double actual;
	private double predicted;
	private String name;
	private AID[] forecastProviders;
	private int forecastResponders;
	private SequentialBehaviour seq;

	@SuppressWarnings("serial")
	protected void setup()
	{
		Object args[] = getArguments();
		register(args[0].toString(), args[1].toString());
		name = args[0].toString();
		forecastProviders = getService("forecast");
		forecastResponders = forecastProviders.length;
		seq = new SequentialBehaviour();
		addBehaviour(seq);
		String msg = "predict," + name;
		ACLMessage getPred = createMessage(forecastResponders, forecastProviders, msg,
				FIPANames.InteractionProtocol.FIPA_REQUEST, ACLMessage.REQUEST);
		AchieveREInitiator a = new AchieveREInitiator(this, getPred)
		{
			protected void handleAgree(ACLMessage agree)
			{
				System.out.println(getLocalName() + ": " + agree.getSender().getName() + " has agreed to the request");
			}

			protected void handleInform(ACLMessage inform)
			{
				System.out.println(getLocalName() + ": " + inform.getSender().getName()
						+ " successfully performed the requested action");
				System.out.println(getLocalName() + ": " + inform.getSender().getName()
						+ "'s predicted energy demand is " + inform.getContent());
				String vals[] = inform.getContent().split(",");
				actual += Double.parseDouble(vals[0]);
				predicted += Double.parseDouble(vals[1]);
			}

			protected void handleRefuse(ACLMessage refuse)
			{
				System.out.println(getLocalName() + ": " + refuse.getSender().getName()
						+ " refused to perform the requested action.");
				forecastResponders--;
			}

			protected void handleFailure(ACLMessage failure)
			{
				if (failure.getSender().equals(myAgent.getAMS()))
				{
					System.out.println(getLocalName() + ": " + "Responder does not exist");
				} else
				{
					System.out.println(getLocalName() + ": " + failure.getSender().getName()
							+ " failed to perform the requested action.");
				}
			}

			protected void handleAllResultNotifications(Vector notifications)
			{
				if (notifications.size() < forecastResponders)
				{
					System.out.println(getLocalName() + ": " + "Timeout expired: missing "
							+ (forecastResponders - notifications.size()) + " responses");
				} else
				{
					System.out.println(getLocalName() + ": " + "Received notifications from every responder");
				}
			}
		};
		System.out.println("Agent " + getLocalName() + " waiting for requests...");
		MessageTemplate template = MessageTemplate.and(
				MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_REQUEST),
				MessageTemplate.MatchPerformative(ACLMessage.REQUEST));
		addBehaviour(new AchieveREResponder(this, template)
		{
			protected ACLMessage prepareResponse(ACLMessage request) throws NotUnderstoodException, RefuseException
			{
				System.out.println("Agent " + getLocalName() + ": REQUEST received from "
						+ request.getSender().getName() + ". Action is " + request.getContent());
				addBehaviour(a);
				return null;
			}

			protected ACLMessage prepareResultNotification(ACLMessage request, ACLMessage response)
					throws FailureException
			{
				String content = Double.toString(actual) + "," + Double.toString(predicted);
				System.out.println("Agent " + getLocalName() + ": Action successfully performed");
				ACLMessage inform = request.createReply();
				inform.setPerformative(ACLMessage.INFORM);
				inform.setContent(content);
				return inform;
			}
		});
	}

	private void register(String name, String type)
	{
		ServiceDescription sd = new ServiceDescription();
		sd.setName(name);
		sd.setType(type);
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		dfd.addServices(sd);
		try
		{
			DFService.register(this, dfd);
		} catch (FIPAException fe)
		{
			fe.printStackTrace();
		}
	}

	protected void takeDown()
	{
		try
		{
			DFService.deregister(this);
		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	private ACLMessage createMessage(int receivers, AID[] agents, String content, String protocol, int type)
	{
		ACLMessage result = new ACLMessage(type);
		for (int i = 0; i < receivers; ++i)
		{
			result.addReceiver(agents[i]);
		}
		result.setProtocol(protocol);
		result.setReplyByDate(new Date(System.currentTimeMillis() + 10000));
		result.setContent(content);
		return result;
	}

	private AID[] getService(String service)
	{
		DFAgentDescription dfd = new DFAgentDescription();
		ServiceDescription sd = new ServiceDescription();
		sd.setType(service);
		dfd.addServices(sd);
		try
		{
			DFAgentDescription[] result = DFService.search(this, dfd);
			AID[] agents = new AID[result.length];
			for (int i = 0; i < result.length; ++i)
			{
				agents[i] = result[i].getName();
			}
			return agents;

		} catch (FIPAException fe)
		{
			fe.printStackTrace();
		}
		return null;
	}
}
