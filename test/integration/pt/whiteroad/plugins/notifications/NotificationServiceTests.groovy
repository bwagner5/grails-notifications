package pt.whiteroad.plugins.notifications

import test.TestSubscriber
import test.TestSubscription

class NotificationServiceTests extends GroovyTestCase {

  def transactional = true
  def notificationService
  def quartzScheduler

  public static final String defaultTopic = "test"
  public static final String defaultSubscriber = "subscriber"

  protected void setUp() {
    super.setUp()
    quartzScheduler.start()

    if(!NotificationTopic.findByTopic(defaultTopic)){
      new NotificationTopic(topic: defaultTopic).save(flush: true)
    }

    if(!TestSubscriber.findByAlias(defaultSubscriber)){
      def channels = [
              new Channel(channelImpl: ChannelType.Email.implementingClass, destination: "nuno.lopes.luis@gmail.com"),
              new Channel(channelImpl: ChannelType.Email.implementingClass, destination: "felix19350@gmail.com"),
              new Channel(channelImpl: ChannelType.Internal.implementingClass, destination: "TEST USER")
      ]
      def subscriber = TestSubscriber.createSubscriber(defaultSubscriber, channels)

      assertNotNull subscriber
      assertEquals subscriber.channels.size(), channels.size()
    }

  }

  protected void tearDown() {
    //Sleep should be here since the sendNotification launches a new thread and the application may begin
    //its shutdown process in the meantime
    sleep 15000
    super.tearDown()
  }

  void testPubSubNotScheduled(){
    createSubscription()

    def oldCount = Notification.count()
    def topic = NotificationTopic.findByTopic(defaultTopic)
    Notification notification = new Notification(message: "PUB/SUB notification", topic: topic)
    notification.save(flush: true)
    assertEquals oldCount+1 , Notification.count()
    notificationService.sendNotification(notification)
  }

  void testPubSubScheduled(){
    createSubscription()

    def oldCount = Notification.count()
    def topic = NotificationTopic.findByTopic(defaultTopic)
    def date = new Date(System.currentTimeMillis() + 10000);

    println "Scheduled to: ${date}"

    Notification notification = new Notification(message: "Scheduled PUB/SUB notification", topic: topic, scheduledDate: date).save(flush: true)
    assertEquals oldCount+1 , Notification.count()
    notificationService.sendNotification(notification)
  }

  /**
   * Test a custom channel implementation. Two new channels are added to the subscriber.
   * */
  void testPubSubCustomNotScheduled(){
    def customTopic = "Strange topic"
    if(!NotificationTopic.findByTopic(customTopic)){
      new NotificationTopic(topic: customTopic).save()
    }

    def channels = [
            new Channel(channelImpl: "pt.whiteroad.plugins.notifications.custom.CustomMailNotification", destination: "felix19350@gmail.com"),
            new Channel(channelImpl: "pt.whiteroad.plugins.notifications.custom.CustomMailNotification", destination: "nuno.lopes.luis@gmail.com")
    ]

    def subscriber = TestSubscriber.findByAlias(defaultSubscriber)
    channels.each{
      subscriber.addToChannels(it)
    }
    subscriber.save(flush: true)

    notificationService.subscribeTopic(subscriber, customTopic, subscriber.channels)

    def oldCount = Notification.count()
    def topic = NotificationTopic.findByTopic(customTopic)
    Notification notification = new Notification(message: "PUB/SUB notification", topic: topic)
    if(!notification.save(flush: true)){
      notification.errors.each{
        System.err.println(it)
      }
    }
    assertEquals oldCount+1 , Notification.count()
    notificationService.sendNotification(notification)
  }

  void testUnsubscribeTopic(){
    createSubscription()

    def oldNum = TestSubscription.count()

    def subscriber = TestSubscriber.findByAlias(defaultSubscriber)
    notificationService.unsubscribeTopic(subscriber, defaultTopic)

    assertEquals(oldNum-1, TestSubscription.count())
  }

  /**
   * Subscribes a topic using all the available communication channels.
   * */
  private void createSubscription(){
    def subscriber = TestSubscriber.findByAlias(defaultSubscriber)
    def notificationTopic = NotificationTopic.findByTopic(defaultTopic)
    if(!TestSubscription.findBySubscriberAndTopic(subscriber, notificationTopic)){
      def oldNumSubscriptions = subscriber?.subscriptions?.count() ?: 0
      def subscription = notificationService.subscribeTopic(subscriber, defaultTopic, subscriber.channels)
      //Force a new instance
      subscriber = TestSubscriber.findByAlias(defaultSubscriber)

      assertNotNull(subscription)
      assertEquals subscription.channels.size(), subscriber.channels.size()
      //Refresh the subscriber and count the subscriptions
      subscriber = TestSubscriber.get(subscriber.id)
      assertEquals subscriber?.subscriptions?.size(), oldNumSubscriptions + 1

    }

  }
}
