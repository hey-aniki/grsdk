#include "HelloWorldScene.h"

USING_NS_CC;

Scene* HelloWorld::createScene()
{
    // 'scene' is an autorelease object
    auto scene = Scene::create();

    // 'layer' is an autorelease object
    auto layer = HelloWorld::create();

    // add layer as a child to scene
    scene->addChild(layer);

    // return the scene
    return scene;
}

// on "init" you need to initialize your instance
bool HelloWorld::init()
{
    //////////////////////////////
    // 1. super init first
    if ( !Layer::init() )
    {
        return false;
    }

    Size visibleSize = Director::getInstance()->getVisibleSize();
    Vec2 origin = Director::getInstance()->getVisibleOrigin();

    /////////////////////////////
    // 2. add a menu item with "X" image, which is clicked to quit the program
    //    you may modify it.

    // add a "close" icon to exit the progress. it's an autorelease object
    auto closeItem = MenuItemImage::create(
                                           "CloseNormal.png",
                                           "CloseSelected.png",
                                           CC_CALLBACK_1(HelloWorld::menuCloseCallback, this));

	closeItem->setPosition(Vec2(origin.x + visibleSize.width - closeItem->getContentSize().width/2 ,
                                origin.y + closeItem->getContentSize().height/2));

    // create menu, it's an autorelease object
    auto menu = Menu::create(closeItem, NULL);
    menu->setPosition(Vec2::ZERO);
    this->addChild(menu, 1);

    /////////////////////////////
    // 3. add your codes below...

    // add a label shows "Hello World"
    // create and initialize a label

    auto label = LabelTTF::create("Hello World", "Arial", 24);

    // position the label on the center of the screen
    label->setPosition(Vec2(origin.x + visibleSize.width/2,
                            origin.y + visibleSize.height - label->getContentSize().height));

    // add the label as a child to this layer
    this->addChild(label, 1);

    // add "HelloWorld" splash screen"
    //auto sprite = Sprite::create("HelloWorld.png");

    // position the sprite on the center of the screen
    //sprite->setPosition(Vec2(visibleSize.width/2 + origin.x, visibleSize.height/2 + origin.y));

    //option 2: load obj and assign the texture
    auto sprite = Sprite3D::create("boss1.obj");
    sprite->setTexture("boss.png");
    sprite->setScale(40.f);

    sprite->setPosition( Vec2(visibleSize.width/2 + origin.x, visibleSize.height/2 + origin.y) );

    //ActionInterval* action;
    float random = CCRANDOM_0_1();

    //if( random < 0.20 )
    //    action = ScaleBy::create(3, 2);
    //else if(random < 0.40)
    //    action = RotateBy::create(3, 360);
    //else if( random < 0.60)
    //    action = Blink::create(1, 3);
    //else if( random < 0.8 )
    //    action = TintBy::create(2, 0, -255, -255);
    //else
    //    action = FadeOut::create(2);

    auto action1 = RotateBy::create(3,  Vec3(360, 0, 0));
    auto action2 = FadeOut::create(2);
    auto action2_back = action2->reverse();
    auto action3 = Blink::create(1, 3);
    auto action3_back = action3->reverse();
    auto seq = Sequence::create( action1, action2, action2_back, action3, action3_back, NULL );

    sprite->runAction( RepeatForever::create(seq) );

    // add the sprite as a child to this layer
    this->addChild(sprite, 0);

    return true;
}


void HelloWorld::menuCloseCallback(Ref* pSender)
{
#if (CC_TARGET_PLATFORM == CC_PLATFORM_WP8) || (CC_TARGET_PLATFORM == CC_PLATFORM_WINRT)
	MessageBox("You pressed the close button. Windows Store Apps do not implement a close button.","Alert");
    return;
#endif

    Director::getInstance()->end();

#if (CC_TARGET_PLATFORM == CC_PLATFORM_IOS)
    exit(0);
#endif
}
