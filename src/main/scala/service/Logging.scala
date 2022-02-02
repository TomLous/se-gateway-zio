package service

object Logging {
//  // service definition
//  type LoggingEnv = Has[Service]
//
//  trait Service {
//    def info[T](message: T):Task[Unit]
//    def debug[T](message: T):Task[Unit]
//    def warn[T](message: T):Task[Unit]
//    def error[T](message: T):Task[Unit]
//  }
//
//  // layer; includes service implementation
//  val live: ZLayer[Has[AppConfig], Nothing, LoggingEnv] = ZLayer.fromService[AppConfig, Service]{
//    appconfig => LoggingLive(appconfig)
//  }
//
//
//  // front-facing API, aka "accessor"
//  def info[T](message: T):ZIO[LoggingEnv, Nothing, Unit] = ZIO.accessM(_.get.info(message))
//  def debug[T](message: T):ZIO[LoggingEnv, Nothing, Unit] = ZIO.accessM(_.get.info(message))
//  def warn[T](message: T):ZIO[LoggingEnv, Nothing, Unit] = ZIO.accessM(_.get.info(message))
//  def error[T](message: T):ZIO[LoggingEnv, Nothing, Unit] = ZIO.accessM(_.get.info(message))
//
//
//  private case class LoggingLive(appConfig: AppConfig) extends Service{
//    val logFormat = "%s"
//    val env = Slf4jLogger.make((context, message) => logFormat.format(message))
//
//    override def info[T](message: T): (Task[Unit] = for{
//      downStreamLogger <- ZIO.service[Logger[String]]
//    } yield ()).pr
//
//    override def debug[T](message: T): Task[Unit] = ???
//
//    override def warn[T](message: T): Task[Unit] = ???
//
//    override def error[T](message: T): Task[Unit] = ???
//  }



}
