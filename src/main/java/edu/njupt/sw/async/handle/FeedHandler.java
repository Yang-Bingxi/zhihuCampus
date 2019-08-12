package edu.njupt.sw.async.handle;

import com.alibaba.fastjson.JSONObject;
import edu.njupt.sw.async.EventHandler;
import edu.njupt.sw.async.EventModel;
import edu.njupt.sw.async.EventType;
import edu.njupt.sw.model.EntityType;
import edu.njupt.sw.model.Feed;
import edu.njupt.sw.model.Question;
import edu.njupt.sw.model.User;
import edu.njupt.sw.service.FeedService;
import edu.njupt.sw.service.FollowService;
import edu.njupt.sw.service.QuestionService;
import edu.njupt.sw.service.UserService;
import edu.njupt.sw.util.JedisAdapter;
import edu.njupt.sw.util.RedisKeyUtil;
import org.apache.commons.lang.math.RandomUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component  //实例化
public class FeedHandler implements EventHandler {
    @Autowired  //自动装配
    FollowService followService;

    @Autowired  //自动装配
    UserService userService;

    @Autowired  //自动装配
    FeedService feedService;

    @Autowired  //自动装配
    JedisAdapter jedisAdapter;

    @Autowired  //自动装配
    QuestionService questionService;


    private String buildFeedData(EventModel model) {
        Map<String, String> map = new HashMap<String ,String>();
        // 触发用户是通用的
        User actor = userService.getUser(model.getActorId());
        if (actor == null) {
            return null;
        }
        map.put("userId", String.valueOf(actor.getId()));
        map.put("userHead", actor.getHeadUrl());
        map.put("userName", actor.getName());

        if (model.getType() == EventType.COMMENT ||
                (model.getType() == EventType.FOLLOW  && model.getEntityType() == EntityType.ENTITY_QUESTION)) {
            Question question = questionService.getById(model.getEntityId());
            if (question == null) {
                return null;
            }
            map.put("questionId", String.valueOf(question.getId()));
            map.put("questionTitle", question.getTitle());
            return JSONObject.toJSONString(map);
        }
        return null;
    }

    @Override   //自动装配
    public void doHandle(EventModel model) {
        // 为了测试，把model的userId随机一下
        Random r = new Random();
        model.setActorId(1+r.nextInt(10));

        // 构造一个新鲜事
        Feed feed = new Feed();
        feed.setCreatedDate(new Date());
        feed.setType(model.getType().getValue());
        feed.setUserId(model.getActorId());
        feed.setData(buildFeedData(model));
        if (feed.getData() == null) {
            // 不支持的feed
            return;
        }
        feedService.addFeed(feed);

        // 获得所有粉丝
        List<Integer> followers = followService.getFollowers(EntityType.ENTITY_USER, model.getActorId(), Integer.MAX_VALUE);
        // 系统队列
        followers.add(0);
        // 给所有粉丝推事件
        for (int follower : followers) {
            String timelineKey = RedisKeyUtil.getTimelineKey(follower);
            jedisAdapter.lpush(timelineKey, String.valueOf(feed.getId()));
            // 限制最长长度，如果timelineKey的长度过大，就删除后面的新鲜事
        }
    }

    @Override   //自动装配
    public List<EventType> getSupportEventTypes() {
        return Arrays.asList(new EventType[]{EventType.COMMENT, EventType.FOLLOW});
    }
}