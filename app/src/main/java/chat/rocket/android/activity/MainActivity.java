package chat.rocket.android.activity;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SlidingPaneLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.view.SimpleDraweeView;

import chat.rocket.android.LaunchUtil;
import chat.rocket.android.R;
import chat.rocket.android.RocketChatCache;
import chat.rocket.android.api.MethodCallHelper;
import chat.rocket.android.fragment.chatroom.HomeFragment;
import chat.rocket.android.fragment.chatroom.RoomFragment;
import chat.rocket.android.fragment.sidebar.SidebarMainFragment;
import chat.rocket.android.helper.KeyboardHelper;
import chat.rocket.android.service.ConnectivityManager;
import chat.rocket.android.widget.RoomToolbar;
import chat.rocket.android.widget.helper.UserAvatarHelper;
import chat.rocket.core.interactors.CanCreateRoomInteractor;
import chat.rocket.core.interactors.RoomInteractor;
import chat.rocket.core.interactors.SessionInteractor;
import chat.rocket.persistence.realm.repositories.RealmRoomRepository;
import chat.rocket.persistence.realm.repositories.RealmSessionRepository;
import chat.rocket.persistence.realm.repositories.RealmUserRepository;
import hugo.weaving.DebugLog;

/**
 * Entry-point for Rocket.Chat.Android application.
 */
public class MainActivity extends AbstractAuthedActivity implements MainContract.View {
  private RoomToolbar toolbar;
  private StatusTicker statusTicker;
  private MainContract.Presenter presenter;

  @Override
  protected int getLayoutContainerForFragment() {
    return R.id.activity_main_container;
  }

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    toolbar = (RoomToolbar) findViewById(R.id.activity_main_toolbar);
    statusTicker = new StatusTicker();
    setupSidebar();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (presenter != null) {
      presenter.bindViewOnly(this);
    }
  }

  @Override
  protected void onPause() {
    if (presenter != null) {
      presenter.release();
    }

    super.onPause();
  }

  private void setupSidebar() {
    SlidingPaneLayout pane = (SlidingPaneLayout) findViewById(R.id.sliding_pane);
    if (pane == null) {
      return;
    }

    final SlidingPaneLayout subPane = (SlidingPaneLayout) findViewById(R.id.sub_sliding_pane);
    if (subPane != null) {
      Button addServerButton = subPane.findViewById(R.id.btn_add_server);
      pane.setPanelSlideListener(new SlidingPaneLayout.SimplePanelSlideListener() {
        @Override
        public void onPanelClosed(View panel) {
          super.onPanelClosed(panel);
          subPane.closePane();
        }
      });

      addServerButton.setOnClickListener(view -> showAddServerActivity());
    }

    toolbar.setNavigationOnClickListener(view -> {
      if (pane.isSlideable() && !pane.isOpen()) {
        pane.openPane();
      }
    });

    //ref: ActionBarDrawerToggle#setProgress
    pane.setPanelSlideListener(new SlidingPaneLayout.PanelSlideListener() {
      @Override
      public void onPanelSlide(View panel, float slideOffset) {
        toolbar.setNavigationIconProgress(slideOffset);
      }

      @Override
      public void onPanelOpened(View panel) {
        toolbar.setNavigationIconVerticalMirror(true);
      }

      @Override
      public void onPanelClosed(View panel) {
        toolbar.setNavigationIconVerticalMirror(false);
        closeUserActionContainer();
      }
    });

    updateServerListBar();
  }

  private void showAddServerActivity() {
    Intent intent = new Intent(this, AddServerActivity.class);
    intent.putExtra(AddServerActivity.EXTRA_FINISH_ON_BACK_PRESS, true);
    startActivity(intent);
  }

  private boolean closeSidebarIfNeeded() {
    // REMARK: Tablet UI doesn't have SlidingPane!
    SlidingPaneLayout pane = (SlidingPaneLayout) findViewById(R.id.sliding_pane);
    if (pane != null && pane.isSlideable() && pane.isOpen()) {
      pane.closePane();
      return true;
    }
    return false;
  }

  @DebugLog
  @Override
  protected void onHostnameUpdated() {
    super.onHostnameUpdated();

    if (presenter != null) {
      presenter.release();
    }

    RoomInteractor roomInteractor = new RoomInteractor(new RealmRoomRepository(hostname));

    CanCreateRoomInteractor createRoomInteractor = new CanCreateRoomInteractor(
        new RealmUserRepository(hostname),
        new SessionInteractor(new RealmSessionRepository(hostname))
    );

    SessionInteractor sessionInteractor = new SessionInteractor(
        new RealmSessionRepository(hostname)
    );


    presenter = new MainPresenter(
        roomInteractor,
        createRoomInteractor,
        sessionInteractor,
        new MethodCallHelper(this, hostname),
        ConnectivityManager.getInstance(getApplicationContext()),
        new RocketChatCache(this)
    );

    updateSidebarMainFragment();

    presenter.bindView(this);
  }

  @DebugLog
  private void updateServerListBar() {
    final SlidingPaneLayout subPane = (SlidingPaneLayout) findViewById(R.id.sub_sliding_pane);
    if (subPane != null) {
      RocketChatCache rocketChatCache = new RocketChatCache(getApplicationContext());
      //TODO: get the server avatar uri and set
      rocketChatCache.addHostname(hostname, null);

      LinearLayout serverListContainer = subPane.findViewById(R.id.server_list_bar);
      for (String serverHostname : rocketChatCache.getServerList()) {
        if (serverListContainer.findViewWithTag(serverHostname) == null) {
          int serverCount = serverListContainer.getChildCount();

          SimpleDraweeView serverButton =
                  (SimpleDraweeView) LayoutInflater.from(this).inflate(R.layout.server_button, serverListContainer, false);
          serverButton.setTag(serverHostname);

          serverButton.setOnClickListener(view -> changeServerIfNeeded(serverHostname));

          Drawable drawable = UserAvatarHelper.INSTANCE.getTextDrawable(serverHostname,this);

          serverButton.getHierarchy().setPlaceholderImage(drawable);
          serverButton.setController(Fresco.newDraweeControllerBuilder().setAutoPlayAnimations(true).build());
          serverListContainer.addView(serverButton, serverCount - 1);
          serverListContainer.requestLayout();
        }
      }
    }
  }

  private void changeServerIfNeeded(String serverHostname) {
    if (!hostname.equalsIgnoreCase(serverHostname)) {
      RocketChatCache rocketChatCache = new RocketChatCache(getApplicationContext());
      rocketChatCache.setSelectedServerHostname(serverHostname);
      recreate();
    }
  }

  private void updateSidebarMainFragment() {
    getSupportFragmentManager().beginTransaction()
        .replace(R.id.sidebar_fragment_container, SidebarMainFragment.create(hostname))
        .commit();
  }

  private void closeUserActionContainer() {
    SidebarMainFragment sidebarFragment = (SidebarMainFragment) getSupportFragmentManager()
            .findFragmentById(R.id.sidebar_fragment_container);
    if (sidebarFragment != null) {
      sidebarFragment.closeUserActionContainer();
    }
  }

  @Override
  protected void onRoomIdUpdated() {
    super.onRoomIdUpdated();
    presenter.onOpenRoom(hostname, roomId);
  }

  @Override
  protected boolean onBackPress() {
    return closeSidebarIfNeeded() || super.onBackPress();
  }

  @Override
  public void showHome() {
    showFragment(new HomeFragment());
  }

  @Override
  public void showRoom(String hostname, String roomId) {
    showFragment(RoomFragment.create(hostname, roomId));
    closeSidebarIfNeeded();
    KeyboardHelper.hideSoftKeyboard(this);
  }

  @Override
  public void showUnreadCount(long roomsCount, int mentionsCount) {
      toolbar.setUnreadBudge((int) roomsCount, mentionsCount);
  }

  @Override
  public void showAddServerScreen() {
    LaunchUtil.showAddServerActivity(this);
  }

  @Override
  public void showLoginScreen() {
    LaunchUtil.showLoginActivity(this, hostname);
    statusTicker.updateStatus(StatusTicker.STATUS_DISMISS, null);
  }

  @Override
  public void showConnectionError() {
    statusTicker.updateStatus(StatusTicker.STATUS_CONNECTION_ERROR,
        Snackbar.make(findViewById(getLayoutContainerForFragment()),
            R.string.fragment_retry_login_error_title, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.fragment_retry_login_retry_title, view ->
                presenter.onRetryLogin()));
  }

  @Override
  public void showConnecting() {
    statusTicker.updateStatus(StatusTicker.STATUS_TOKEN_LOGIN,
        Snackbar.make(findViewById(getLayoutContainerForFragment()),
            R.string.server_config_activity_authenticating, Snackbar.LENGTH_INDEFINITE));
  }

  @Override
  public void showConnectionOk() {
    statusTicker.updateStatus(StatusTicker.STATUS_DISMISS, null);
  }

  //TODO: consider this class to define in layouthelper for more complicated operation.
  private static class StatusTicker {
    public static final int STATUS_DISMISS = 0;
    public static final int STATUS_CONNECTION_ERROR = 1;
    public static final int STATUS_TOKEN_LOGIN = 2;

    private int status;
    private Snackbar snackbar;

    public StatusTicker() {
      status = STATUS_DISMISS;
    }

    public void updateStatus(int status, Snackbar snackbar) {
      if (status == this.status) {
        return;
      }
      this.status = status;
      if (this.snackbar != null) {
        this.snackbar.dismiss();
      }
      if (status != STATUS_DISMISS) {
        this.snackbar = snackbar;
        if (this.snackbar != null) {
          this.snackbar.show();
        }
      }
    }
  }
}
